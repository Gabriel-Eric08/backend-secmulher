package com.secmulher.modules.core.domain.services;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.secmulher.modules.core.domain.access.CategoryAccess;
import com.secmulher.modules.core.domain.dtos.DynamicEntityDtos.DynamicEntitySearchRequest;
import com.secmulher.modules.core.domain.dtos.DynamicEntityDtos.FieldFilter;
import com.secmulher.modules.core.domain.dtos.DynamicEntityDtos.SortRequest;
import com.secmulher.modules.core.domain.enums.FilterOperator;
import com.secmulher.modules.core.domain.exceptions.FieldValidationException;
import com.secmulher.modules.core.domain.exceptions.ForbiddenException;
import com.secmulher.modules.core.infra.entities.CategoryField;
import com.secmulher.modules.core.infra.repositories.DynamicEntitySearchRepository.Criterion;
import com.secmulher.modules.core.infra.repositories.DynamicEntitySearchRepository.Sort;

// Valida os filtros e a ordenação de uma busca contra os campos da categoria e converte
// os valores para o tipo de cada campo. Erros de formato voltam todos juntos (400);
// filtrar ou ordenar por campo sem permissão de leitura é proibido (403), pois revelaria o valor.
final class DynamicEntityFilterCompiler {
    static final int MAX_FILTERS = 30;
    static final int MAX_IN_VALUES = 100;
    private static final Map<String, String> SORT_COLUMNS = Map.of("createdAt", "created_at", "updatedAt", "updated_at");

    record Compiled(List<Criterion> criteria, Sort sort) {}

    private final Map<String, CategoryField> fields;
    private final Map<String, String> errors = new LinkedHashMap<>();

    private DynamicEntityFilterCompiler(List<CategoryField> fields) {
        this.fields = fields.stream().collect(Collectors.toMap(CategoryField::getKey, Function.identity()));
    }

    static Compiled compile(DynamicEntitySearchRequest request, List<CategoryField> categoryFields, CategoryAccess access) {
        return new DynamicEntityFilterCompiler(categoryFields).run(request, access);
    }

    private Compiled run(DynamicEntitySearchRequest request, CategoryAccess access) {
        // "fields" é um atalho: cada par vira um filtro EQUALS
        List<FieldFilter> filters = new ArrayList<>();
        if (request.fields() != null) {
            request.fields().forEach((key, value) -> filters.add(new FieldFilter(key, FilterOperator.EQUALS, value)));
        }
        if (request.filters() != null) {
            filters.addAll(request.filters());
        }
        if (filters.size() > MAX_FILTERS) {
            errors.put("filters", "no máximo " + MAX_FILTERS + " filtros por busca");
            throw new FieldValidationException("Filtros de busca inválidos", errors);
        }

        Set<String> hidden = new TreeSet<>();
        filters.stream().map(FieldFilter::field).filter(fields::containsKey)
            .filter(key -> !access.canReadColumn(key)).forEach(hidden::add);
        SortRequest sortRequest = request.sort();
        if (sortRequest != null && fields.containsKey(sortRequest.field()) && !access.canReadColumn(sortRequest.field())) {
            hidden.add(sortRequest.field());
        }
        if (!hidden.isEmpty()) {
            throw new ForbiddenException("Sem permissão para filtrar ou ordenar por: " + String.join(", ", hidden));
        }

        List<Criterion> criteria = new ArrayList<>();
        for (int i = 0; i < filters.size(); i++) {
            Criterion criterion = criterion("filters[" + i + "]", filters.get(i));
            if (criterion != null) {
                criteria.add(criterion);
            }
        }
        Sort sort = sort(sortRequest);

        if (!errors.isEmpty()) {
            throw new FieldValidationException("Filtros de busca inválidos", errors);
        }
        return new Compiled(criteria, sort);
    }

    private Criterion criterion(String path, FieldFilter filter) {
        CategoryField field = fields.get(filter.field());
        if (field == null) {
            return fail(path, "campo '" + filter.field() + "' não existe nesta categoria");
        }
        FilterOperator operator = filter.operator();
        if (!FilterOperator.forType(field.getFieldType()).contains(operator)) {
            return fail(path, "operador " + operator + " não se aplica a campos " + field.getFieldType()
                + "; use um de " + FilterOperator.forType(field.getFieldType()));
        }
        if (!operator.needsValue()) {
            return new Criterion(field.getKey(), field.getFieldType(), operator, List.of());
        }

        Object value = filter.value();
        List<Object> values = new ArrayList<>();
        switch (operator) {
            case IN -> {
                if (!(value instanceof Collection<?> items) || items.isEmpty() || items.size() > MAX_IN_VALUES) {
                    return fail(path, "IN exige uma lista com 1 a " + MAX_IN_VALUES + " valores");
                }
                for (Object item : items) {
                    Object converted = convert(field, item, operator);
                    if (converted == null) {
                        return fail(path, invalidValue(field, item));
                    }
                    values.add(converted);
                }
            }
            case BETWEEN -> {
                if (!(value instanceof List<?> range) || range.size() != 2) {
                    return fail(path, "BETWEEN exige uma lista [mínimo, máximo]");
                }
                Object min = convert(field, range.get(0), operator);
                Object max = convert(field, range.get(1), operator);
                if (min == null || max == null) {
                    return fail(path, invalidValue(field, min == null ? range.get(0) : range.get(1)));
                }
                if (compare(min, max) > 0) {
                    return fail(path, "em BETWEEN o mínimo não pode ser maior que o máximo");
                }
                values.add(min);
                values.add(max);
            }
            default -> {
                Object converted = convert(field, value, operator);
                if (converted == null) {
                    return fail(path, invalidValue(field, value));
                }
                values.add(converted);
            }
        }
        return new Criterion(field.getKey(), field.getFieldType(), operator, values);
    }

    // Devolve o valor no tipo usado pela consulta, ou null se não servir para o campo
    private static Object convert(CategoryField field, Object value, FilterOperator operator) {
        if (value == null) {
            return null;
        }
        return switch (field.getFieldType()) {
            case TEXT -> value instanceof String text
                && !(text.isBlank() && (operator == FilterOperator.CONTAINS || operator == FilterOperator.STARTS_WITH))
                ? text : null;
            case LIST -> value instanceof String text && field.getOptions() != null && field.getOptions().contains(text)
                ? text : null;
            case BOOLEAN -> value instanceof Boolean flag ? flag.toString()
                : "true".equals(value) || "false".equals(value) ? value : null;
            case NUMBER -> toNumber(value);
            case DATE -> toDate(value);
        };
    }

    private static BigDecimal toNumber(Object value) {
        if (value instanceof Number || value instanceof String) {
            try {
                return new BigDecimal(value.toString().trim());
            } catch (NumberFormatException ex) {
                return null;
            }
        }
        return null;
    }

    private static LocalDate toDate(Object value) {
        if (value instanceof String text) {
            try {
                return LocalDate.parse(text);
            } catch (DateTimeParseException ex) {
                return null;
            }
        }
        return null;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static int compare(Object a, Object b) {
        return ((Comparable) a).compareTo(b);
    }

    private static String invalidValue(CategoryField field, Object value) {
        return switch (field.getFieldType()) {
            case TEXT -> "valor deve ser um texto (não vazio em CONTAINS/STARTS_WITH)";
            case NUMBER -> "valor deve ser um número";
            case DATE -> "valor deve ser uma data no formato AAAA-MM-DD";
            case BOOLEAN -> "valor deve ser true ou false";
            case LIST -> "'" + value + "' não é uma das opções: " + field.getOptions();
        };
    }

    private Sort sort(SortRequest request) {
        if (request == null) {
            return new Sort(null, null, "created_at", true);
        }
        String column = SORT_COLUMNS.get(request.field());
        if (column != null) {
            return new Sort(null, null, column, request.descending());
        }
        CategoryField field = fields.get(request.field());
        if (field == null) {
            errors.put("sort.field", "use createdAt, updatedAt ou um campo da categoria");
            return null;
        }
        return new Sort(field.getKey(), field.getFieldType(), null, request.descending());
    }

    private Criterion fail(String path, String message) {
        errors.put(path, message);
        return null;
    }
}

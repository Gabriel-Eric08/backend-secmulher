package com.secmulher.modules.core.infra.repositories;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.UUID;

import org.springframework.stereotype.Repository;

import com.secmulher.modules.core.domain.enums.FilterOperator;
import com.secmulher.modules.core.infra.entities.DynamicEntity;
import com.secmulher.modules.core.infra.entities.enums.FieldType;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;

// Busca de entidades dinâmicas com filtros tipados sobre o JSONB custom_fields.
// Recebe critérios já validados e convertidos pelo service; tudo vai como parâmetro (chaves inclusive).
@Repository
public class DynamicEntitySearchRepository {

    // values já convertidos: String (TEXT/LIST/BOOLEAN), BigDecimal (NUMBER) ou LocalDate (DATE).
    // IN usa a lista inteira, BETWEEN os dois primeiros itens, IS_EMPTY/NOT_EMPTY nenhum.
    public record Criterion(String key, FieldType type, FilterOperator operator, List<Object> values) {}

    // key nulo ordena pela coluna (created_at/updated_at); senão, pelo campo customizado
    public record Sort(String key, FieldType type, String column, boolean descending) {}

    @PersistenceContext
    EntityManager entityManager;

    public List<DynamicEntity> search(UUID categoryId, List<Criterion> criteria, Sort sort) {
        StringBuilder sql = new StringBuilder("SELECT * FROM dynamic_entities WHERE category_id = :categoryId");
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("categoryId", categoryId);

        for (int i = 0; i < criteria.size(); i++) {
            Criterion criterion = criteria.get(i);
            String k = "k" + i;
            String v = "v" + i;
            params.put(k, criterion.key());
            sql.append(" AND ").append(condition(criterion, k, v, params));
        }

        sql.append(" ORDER BY ");
        if (sort.key() != null) {
            params.put("sortKey", sort.key());
            sql.append(valueExpression(sort.type(), "sortKey"))
                .append(sort.descending() ? " DESC" : " ASC").append(" NULLS LAST, ");
        } else {
            sql.append(sort.column()).append(sort.descending() ? " DESC" : " ASC").append(", ");
        }
        sql.append("created_at DESC, id");

        Query query = entityManager.createNativeQuery(sql.toString(), DynamicEntity.class);
        params.forEach(query::setParameter);
        @SuppressWarnings("unchecked")
        List<DynamicEntity> result = query.getResultList();
        return result;
    }

    private static String condition(Criterion criterion, String k, String v, Map<String, Object> params) {
        String expr = valueExpression(criterion.type(), k);
        List<Object> values = criterion.values();
        return switch (criterion.operator()) {
            case IS_EMPTY -> "NOT jsonb_exists(custom_fields, :" + k + ")";
            case NOT_EMPTY -> "jsonb_exists(custom_fields, :" + k + ")";
            case IN -> {
                params.put(v, new ArrayList<>(values));
                yield expr + " IN (:" + v + ")";
            }
            case BETWEEN -> {
                params.put(v + "a", values.get(0));
                params.put(v + "b", values.get(1));
                yield expr + " BETWEEN :" + v + "a AND :" + v + "b";
            }
            case CONTAINS -> {
                params.put(v, "%" + escapeLike((String) values.get(0)) + "%");
                yield expr + " ILIKE :" + v;
            }
            case STARTS_WITH -> {
                params.put(v, escapeLike((String) values.get(0)) + "%");
                yield expr + " ILIKE :" + v;
            }
            case NOT_EQUALS -> {
                params.put(v, values.get(0));
                yield "(" + expr + " IS NULL OR " + expr + " <> :" + v + ")";
            }
            default -> {
                params.put(v, values.get(0));
                yield expr + " " + comparison(criterion.operator()) + " :" + v;
            }
        };
    }

    // Converte o valor do JSONB para o tipo SQL do campo; valores fora do formato viram NULL em vez de erro
    private static String valueExpression(FieldType type, String keyParam) {
        String text = "(custom_fields ->> :" + keyParam + ")";
        return switch (type) {
            case NUMBER -> "(CASE WHEN jsonb_typeof(custom_fields -> :" + keyParam + ") = 'number' THEN CAST("
                + text + " AS numeric) END)";
            case DATE -> "(CASE WHEN " + text + " ~ '^[0-9]{4}-[0-9]{2}-[0-9]{2}$' THEN CAST("
                + text + " AS date) END)";
            case TEXT, LIST, BOOLEAN -> text;
        };
    }

    private static String comparison(FilterOperator operator) {
        return switch (operator) {
            case EQUALS -> "=";
            case GT -> ">";
            case GTE -> ">=";
            case LT -> "<";
            case LTE -> "<=";
            default -> throw new IllegalArgumentException("Operador sem comparação direta: " + operator);
        };
    }

    // O padrão do ILIKE no Postgres usa \ como escape
    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}

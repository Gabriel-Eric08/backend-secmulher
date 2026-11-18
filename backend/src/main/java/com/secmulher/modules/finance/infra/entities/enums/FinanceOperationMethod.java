package com.secmulher.modules.finance.infra.entities.enums;

// Tipos de operação financeira. O nome do enum é gravado em finance_operations.method
public enum FinanceOperationMethod {
    OPENING("Valor inicial da ação"),
    TRANSFER_BETWEEN_SUB_ACTIONS("Método A - Transferência entre subações"),
    TRANSFER_BETWEEN_ACTIONS("Método B - Transferência entre ações"),
    TAC("Método C - Adição de valor à ação (TAC)"),
    DISTRIBUTION("Distribuição do saldo da ação para subações"),
    COMMITMENT("Empenho"),
    COMMITMENT_CANCELLATION("Anulação de empenho"),
    LIQUIDATION("Liquidação"),
    PAYMENT("Pagamento");

    private final String label;

    FinanceOperationMethod(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}

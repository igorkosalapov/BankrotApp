package com.bankrotapp.openai;

/**
 * Text blocks used only as auxiliary sections in the bankruptcy statement preview.
 */
public record OpenAiTextBlocks(
        String hardshipReason,
        String employmentIncomeDescription,
        String loanFundsUsageDescription,
        boolean generatedByOpenAi
) {
}

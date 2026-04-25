package com.bankrotapp.model;

import java.util.List;

public record BankruptcyApplicationData(
        Debtor debtor,
        List<Creditor> creditors,
        FamilyInfo familyInfo,
        EmploymentInfo employmentInfo,
        PropertyInfo propertyInfo
) {
}

package com.bankrotapp.model;

public class PreviewFormData {
    private String fullName = "";
    private String lastName = "";
    private String firstName = "";
    private String middleName = "";
    private String birthDate = "";
    private String birthPlace = "";
    private String snils = "";
    private String inn = "";

    private String passportSeries = "";
    private String passportNumber = "";
    private String passportIssuedBy = "";
    private String passportIssueDate = "";
    private String passportDivisionCode = "";

    private String registrationPostalCode = "";
    private String registrationRegion = "";
    private String registrationDistrict = "";
    private String registrationCity = "";
    private String registrationSettlement = "";
    private String registrationStreet = "";
    private String registrationHouse = "";
    private String registrationBuilding = "";
    private String registrationApartment = "";
    private String registrationFullAddress = "";

    private String creditorLines = "";

    private String maritalStatus = "SINGLE";
    private String spouseName = "";
    private String marriageDate = "";
    private String marriageCertificate = "";
    private String divorceDate = "";
    private String divorceCertificate = "";
    private String spouseDeathDate = "";
    private String deathCertificate = "";

    private String childrenLines = "";
    private String realEstateLines = "";
    private String vehicleLines = "";

    private String employmentStatus = "EMPLOYED";
    private String employerName = "";
    private String position = "";
    private String monthlyIncome = "";
    private String income2022 = "";
    private String income2023 = "";
    private String income2024 = "";
    private String income2025 = "";
    private String previousWorkDescription = "";

    private String egripCertificate = "";
    private String gibddResponse = "";
    private String egrnExtract = "";
    private String passportPages = "";
    private String innPages = "";
    private String snilsPages = "";
    private String sziIlsPages = "";
    private String bankAccountsPages = "";
    private String taxCertificatePages = "";
    private String criminalRecordPages = "";
    private String creditorPostingProofPages = "";

    private String hardshipReasonInput = "";
    private String employmentIncomeInput = "";
    private String loanFundsUsageInput = "";

    public String getFullName() {
        if (!safe(fullName).isBlank()) {
            return safe(fullName);
        }
        return String.join(" ", safe(lastName), safe(firstName), safe(middleName)).trim().replaceAll("\\s+", " ");
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    // getters and setters
    public String getFullNameRaw() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }
    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }
    public String getMiddleName() { return middleName; }
    public void setMiddleName(String middleName) { this.middleName = middleName; }
    public String getBirthDate() { return birthDate; }
    public void setBirthDate(String birthDate) { this.birthDate = birthDate; }
    public String getBirthPlace() { return birthPlace; }
    public void setBirthPlace(String birthPlace) { this.birthPlace = birthPlace; }
    public String getSnils() { return snils; }
    public void setSnils(String snils) { this.snils = snils; }
    public String getInn() { return inn; }
    public void setInn(String inn) { this.inn = inn; }
    public String getPassportSeries() { return passportSeries; }
    public void setPassportSeries(String passportSeries) { this.passportSeries = passportSeries; }
    public String getPassportNumber() { return passportNumber; }
    public void setPassportNumber(String passportNumber) { this.passportNumber = passportNumber; }
    public String getPassportIssuedBy() { return passportIssuedBy; }
    public void setPassportIssuedBy(String passportIssuedBy) { this.passportIssuedBy = passportIssuedBy; }
    public String getPassportIssueDate() { return passportIssueDate; }
    public void setPassportIssueDate(String passportIssueDate) { this.passportIssueDate = passportIssueDate; }
    public String getPassportDivisionCode() { return passportDivisionCode; }
    public void setPassportDivisionCode(String passportDivisionCode) { this.passportDivisionCode = passportDivisionCode; }
    public String getRegistrationPostalCode() { return registrationPostalCode; }
    public void setRegistrationPostalCode(String registrationPostalCode) { this.registrationPostalCode = registrationPostalCode; }
    public String getRegistrationRegion() { return registrationRegion; }
    public void setRegistrationRegion(String registrationRegion) { this.registrationRegion = registrationRegion; }
    public String getRegistrationDistrict() { return registrationDistrict; }
    public void setRegistrationDistrict(String registrationDistrict) { this.registrationDistrict = registrationDistrict; }
    public String getRegistrationCity() { return registrationCity; }
    public void setRegistrationCity(String registrationCity) { this.registrationCity = registrationCity; }
    public String getRegistrationSettlement() { return registrationSettlement; }
    public void setRegistrationSettlement(String registrationSettlement) { this.registrationSettlement = registrationSettlement; }
    public String getRegistrationStreet() { return registrationStreet; }
    public void setRegistrationStreet(String registrationStreet) { this.registrationStreet = registrationStreet; }
    public String getRegistrationHouse() { return registrationHouse; }
    public void setRegistrationHouse(String registrationHouse) { this.registrationHouse = registrationHouse; }
    public String getRegistrationBuilding() { return registrationBuilding; }
    public void setRegistrationBuilding(String registrationBuilding) { this.registrationBuilding = registrationBuilding; }
    public String getRegistrationApartment() { return registrationApartment; }
    public void setRegistrationApartment(String registrationApartment) { this.registrationApartment = registrationApartment; }
    public String getRegistrationFullAddress() { return registrationFullAddress; }
    public void setRegistrationFullAddress(String registrationFullAddress) { this.registrationFullAddress = registrationFullAddress; }
    public String getCreditorLines() { return creditorLines; }
    public void setCreditorLines(String creditorLines) { this.creditorLines = creditorLines; }
    public String getMaritalStatus() { return maritalStatus; }
    public void setMaritalStatus(String maritalStatus) { this.maritalStatus = maritalStatus; }
    public String getSpouseName() { return spouseName; }
    public void setSpouseName(String spouseName) { this.spouseName = spouseName; }
    public String getMarriageDate() { return marriageDate; }
    public void setMarriageDate(String marriageDate) { this.marriageDate = marriageDate; }
    public String getMarriageCertificate() { return marriageCertificate; }
    public void setMarriageCertificate(String marriageCertificate) { this.marriageCertificate = marriageCertificate; }
    public String getDivorceDate() { return divorceDate; }
    public void setDivorceDate(String divorceDate) { this.divorceDate = divorceDate; }
    public String getDivorceCertificate() { return divorceCertificate; }
    public void setDivorceCertificate(String divorceCertificate) { this.divorceCertificate = divorceCertificate; }
    public String getSpouseDeathDate() { return spouseDeathDate; }
    public void setSpouseDeathDate(String spouseDeathDate) { this.spouseDeathDate = spouseDeathDate; }
    public String getDeathCertificate() { return deathCertificate; }
    public void setDeathCertificate(String deathCertificate) { this.deathCertificate = deathCertificate; }
    public String getChildrenLines() { return childrenLines; }
    public void setChildrenLines(String childrenLines) { this.childrenLines = childrenLines; }
    public String getRealEstateLines() { return realEstateLines; }
    public void setRealEstateLines(String realEstateLines) { this.realEstateLines = realEstateLines; }
    public String getVehicleLines() { return vehicleLines; }
    public void setVehicleLines(String vehicleLines) { this.vehicleLines = vehicleLines; }
    public String getEmploymentStatus() { return employmentStatus; }
    public void setEmploymentStatus(String employmentStatus) { this.employmentStatus = employmentStatus; }
    public String getEmployerName() { return employerName; }
    public void setEmployerName(String employerName) { this.employerName = employerName; }
    public String getPosition() { return position; }
    public void setPosition(String position) { this.position = position; }
    public String getMonthlyIncome() { return monthlyIncome; }
    public void setMonthlyIncome(String monthlyIncome) { this.monthlyIncome = monthlyIncome; }
    public String getIncome2022() { return income2022; }
    public void setIncome2022(String income2022) { this.income2022 = income2022; }
    public String getIncome2023() { return income2023; }
    public void setIncome2023(String income2023) { this.income2023 = income2023; }
    public String getIncome2024() { return income2024; }
    public void setIncome2024(String income2024) { this.income2024 = income2024; }
    public String getIncome2025() { return income2025; }
    public void setIncome2025(String income2025) { this.income2025 = income2025; }
    public String getPreviousWorkDescription() { return previousWorkDescription; }
    public void setPreviousWorkDescription(String previousWorkDescription) { this.previousWorkDescription = previousWorkDescription; }
    public String getEgripCertificate() { return egripCertificate; }
    public void setEgripCertificate(String egripCertificate) { this.egripCertificate = egripCertificate; }
    public String getGibddResponse() { return gibddResponse; }
    public void setGibddResponse(String gibddResponse) { this.gibddResponse = gibddResponse; }
    public String getEgrnExtract() { return egrnExtract; }
    public void setEgrnExtract(String egrnExtract) { this.egrnExtract = egrnExtract; }
    public String getPassportPages() { return passportPages; }
    public void setPassportPages(String passportPages) { this.passportPages = passportPages; }
    public String getInnPages() { return innPages; }
    public void setInnPages(String innPages) { this.innPages = innPages; }
    public String getSnilsPages() { return snilsPages; }
    public void setSnilsPages(String snilsPages) { this.snilsPages = snilsPages; }
    public String getSziIlsPages() { return sziIlsPages; }
    public void setSziIlsPages(String sziIlsPages) { this.sziIlsPages = sziIlsPages; }
    public String getBankAccountsPages() { return bankAccountsPages; }
    public void setBankAccountsPages(String bankAccountsPages) { this.bankAccountsPages = bankAccountsPages; }
    public String getTaxCertificatePages() { return taxCertificatePages; }
    public void setTaxCertificatePages(String taxCertificatePages) { this.taxCertificatePages = taxCertificatePages; }
    public String getCriminalRecordPages() { return criminalRecordPages; }
    public void setCriminalRecordPages(String criminalRecordPages) { this.criminalRecordPages = criminalRecordPages; }
    public String getCreditorPostingProofPages() { return creditorPostingProofPages; }
    public void setCreditorPostingProofPages(String creditorPostingProofPages) { this.creditorPostingProofPages = creditorPostingProofPages; }
    public String getHardshipReasonInput() { return hardshipReasonInput; }
    public void setHardshipReasonInput(String hardshipReasonInput) { this.hardshipReasonInput = hardshipReasonInput; }
    public String getEmploymentIncomeInput() { return employmentIncomeInput; }
    public void setEmploymentIncomeInput(String employmentIncomeInput) { this.employmentIncomeInput = employmentIncomeInput; }
    public String getLoanFundsUsageInput() { return loanFundsUsageInput; }
    public void setLoanFundsUsageInput(String loanFundsUsageInput) { this.loanFundsUsageInput = loanFundsUsageInput; }
}

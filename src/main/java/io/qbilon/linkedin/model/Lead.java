package io.qbilon.linkedin.model;

import eu.easyrpa.openframework.excel.annotations.ExcelColumn;

public class Lead {
    @ExcelColumn(name = "Vorname")
    private String firstName;
    @ExcelColumn(name = "Nachname")
    private String lastName;
    @ExcelColumn(name = "E-Mail")
    private String email;
    @ExcelColumn(name = "Jobbezeichnung")
    private String jobTitle;
    @ExcelColumn(name = "Job 1")
    private String previousJobTitle1;
    @ExcelColumn(name = "Job 2")
    private String previousJobTitle2;
    @ExcelColumn(name = "Job 3")
    private String previousJobTitle3;
    @ExcelColumn(name = "LinkedIn Profil")
    private String profileLink;
    @ExcelColumn(name = "Branche")
    private String industry;

    // just for us
    @ExcelColumn(name = "Lifecycle Phase")
    private String lifecyclePhase = "Lead";
    @ExcelColumn(name = "Leadstatus")
    private String leadStatus = "Recherchiert";
    @ExcelColumn(name = "Für Kontakt zuständiger Mitarbeiter")
    private String user = "";


    public String getFirstName() {
        return firstName;
    }
    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }
    public String getLastName() {
        return lastName;
    }
    public void setLastName(String lastName) {
        this.lastName = lastName;
    }
    public String getEmail() {
        return email;
    }
    public void setEmail(String email) {
        this.email = email;
    }
    public String getJobTitle() {
        return jobTitle;
    }
    public void setJobTitle(String jobTitle) {
        this.jobTitle = jobTitle;
    }
    public String getPreviousJobTitle1() {
        return previousJobTitle1;
    }
    public void setPreviousJobTitle1(String previousJobTitle1) {
        this.previousJobTitle1 = previousJobTitle1;
    }
    public String getPreviousJobTitle2() {
        return previousJobTitle2;
    }
    public void setPreviousJobTitle2(String previousJobTitle2) {
        this.previousJobTitle2 = previousJobTitle2;
    }
    public String getPreviousJobTitle3() {
        return previousJobTitle3;
    }
    public void setPreviousJobTitle3(String previousJobTitle3) {
        this.previousJobTitle3 = previousJobTitle3;
    }
    public String getProfileLink() {
        return profileLink;
    }
    public void setProfileLink(String profileLink) {
        this.profileLink = profileLink;
    }
    public String getIndustry() {
        return industry;
    }
    public void setIndustry(String industry) {
        this.industry = industry;
    }
    public String getLifecyclePhase() {
        return lifecyclePhase;
    }
    public void setLifecyclePhase(String lifecyclePhase) {
        this.lifecyclePhase = lifecyclePhase;
    }
    public String getLeadStatus() {
        return leadStatus;
    }
    public void setLeadStatus(String leadStatus) {
        this.leadStatus = leadStatus;
    }
    public String getUser() {
        return user;
    }
    public void setUser(String user) {
        this.user = user;
    }
}

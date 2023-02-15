package io.qbilon.linkedin.model;

import eu.easyrpa.openframework.excel.annotations.ExcelColumn;

public class Company {
    @ExcelColumn(name = "Company")
    private String name;
    @ExcelColumn(name = "Link")
    private String link;
    @ExcelColumn(name = "Industry")
    private String industry;
    @ExcelColumn(name = "Size")
    private String size;
    @ExcelColumn(name = "Employees on LinkedIn")
    private String employeesOnLinkedIn;
    @ExcelColumn(name = "Domain")
    private String domain;

    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }
    public String getLink() {
        return link;
    }
    public void setLink(String link) {
        this.link = link;
    }
    public String getDomain() {
        return domain;
    }
    public void setDomain(String domain) {
        this.domain = domain;
    }
    public String getIndustry() {
        return industry;
    }
    public void setIndustry(String industry) {
        this.industry = industry;
    }
    public String getSize() {
        return size;
    }
    public void setSize(String size) {
        this.size = size;
    }
    public String getEmployeesOnLinkedIn() {
        return employeesOnLinkedIn;
    }
    public void setEmployeesOnLinkedIn(String employeesOnLinkedIn) {
        this.employeesOnLinkedIn = employeesOnLinkedIn;
    }
}

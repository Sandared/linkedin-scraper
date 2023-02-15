package io.qbilon.linkedin.model;

import eu.easyrpa.openframework.excel.annotations.ExcelColumn;

public class Contact {
    @ExcelColumn(name = "E-Mail")
    private String email;

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }
}

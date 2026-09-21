package com.costplatform.lite.extension;

/** Query contract passed to a host option provider. */
public class CostLiteOptionQuery {
    private Long variableId;
    private String variableCode;
    private String optionSourceType;
    private String optionSourceCode;
    private String optionConfigJson;
    private String keyword;
    private int pageNum = 1;
    private int pageSize = 50;

    public Long getVariableId() {
        return variableId;
    }

    public void setVariableId(Long variableId) {
        this.variableId = variableId;
    }

    public String getVariableCode() {
        return variableCode;
    }

    public void setVariableCode(String variableCode) {
        this.variableCode = variableCode;
    }

    public String getOptionSourceType() {
        return optionSourceType;
    }

    public void setOptionSourceType(String optionSourceType) {
        this.optionSourceType = optionSourceType;
    }

    public String getOptionSourceCode() {
        return optionSourceCode;
    }

    public void setOptionSourceCode(String optionSourceCode) {
        this.optionSourceCode = optionSourceCode;
    }

    public String getOptionConfigJson() {
        return optionConfigJson;
    }

    public void setOptionConfigJson(String optionConfigJson) {
        this.optionConfigJson = optionConfigJson;
    }

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    public int getPageNum() {
        return pageNum;
    }

    public void setPageNum(int pageNum) {
        this.pageNum = pageNum;
    }

    public int getPageSize() {
        return pageSize;
    }

    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
    }
}

package com.costplatform.lite.extension;

/**
 * Optional host-side adapter for business dictionaries and large business
 * master data. The lightweight platform never copies those tables into its
 * own dictionary table.
 */
public interface CostLiteOptionProvider {
    boolean supports(String optionSourceType, String optionSourceCode);

    CostLiteOptionPage query(CostLiteOptionQuery query);
}

package com.costplatform.lite.extension;

/**
 * Optional host-side adapter for business dictionaries and large business
 * master data.
 *
 * <p>The lightweight platform never copies the host table into its own
 * dictionary table. The host decides how {@code optionSourceCode} is routed
 * to a dictionary service or a paginated master-data query.</p>
 */
public interface CostLiteOptionProvider {
    /**
     * Whether this provider owns the requested option catalog.
     *
     * @param optionSourceType BUSINESS_DICT or BUSINESS_MASTER
     * @param optionSourceCode host-defined dictionary/table catalog code
     * @return true when this provider can serve the catalog
     */
    boolean supports(String optionSourceType, String optionSourceCode);

    /**
     * Query options for the maintenance UI.
     *
     * @param query normalized variable and paging information
     * @return selectable option page
     */
    CostLiteOptionPage query(CostLiteOptionQuery query);
}

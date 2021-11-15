package fi.nls.oskari;

import fi.mml.portti.service.search.*;
import fi.nls.oskari.util.ConversionHelper;
import fi.nls.oskari.util.JSONHelper;
import org.apache.commons.text.StringEscapeUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.safety.Whitelist;

import java.util.List;

public class SearchWorker {

    public static final String KEY_TOTAL_COUNT = "totalCount";
    public static final String KEY_ERROR_TEXT = "errorText";
    public static final String KEY_LOCATIONS = "locations";
    public static final String KEY_METHODS = "methods";
    public static final String KEY_HAS_MORE = "hasMore";


    public static final String ERR_EMPTY = "cannot_be_empty";
    public static final String ERR_TOO_SHORT = "too_short";
    public static final String ERR_TOO_WILD = "too_many_stars";

    public static final String STR_TRUE = "true";
    public static final String STR_NULL = "null";
    /** Our service */
    private static SearchService searchService = new SearchServiceImpl();

    /**
     * Checks if search was legal
     * 
     * @param searchString
     * @return
     */
    public static String checkLegalSearch(String searchString) {

        if (searchString == null || searchString.isEmpty()) {
            return ERR_EMPTY;
        }
        searchString = Jsoup.clean(searchString, Whitelist.none());
        searchString = StringEscapeUtils.unescapeHtml4(searchString);
        if (searchString.contains("*") && searchString.length() <= 4) {
            return ERR_TOO_SHORT;
        }
        if (ConversionHelper.count(searchString, "*") > 2) {
            return ERR_TOO_WILD;
        }
        return STR_TRUE;
    }

    /**
     * Returns the maximum amount of search results that should be queried.
     * If requested is negative value, returns configured max results count.
     * Requested count is checked against result hard limit which might be lower than the client requests
     * Returns the parameter value if it's not -1 or more than the maximum result count.
     * @param requested
     * @return maximum value of results or requested, which ever is smaller.
     */
    public static int getMaxResults(int requested) {
        int hardLimit = searchService.getMaxResultsHardLimit();
        int maximum = searchService.getMaxResultsCount();
        if (requested < 0) {
            return maximum;
        } else if (hardLimit > 0 && requested > hardLimit) {
            return hardLimit;
        }
        return requested;
    }

    /**
     * Makes a search based on criteria. Picks the configured/requested amount of results and
     * serializes the result to JSON.
     * @param sc
     * @return result - Search results
     */
    public static JSONObject doSearch(final SearchCriteria sc) {

        Query query = searchService.doSearch(sc);
        int maxResults = getMaxResults(sc.getMaxResults());
        List<SearchResultItem> items = query.getSortedResults(maxResults + 1);

        JSONObject result = new JSONObject();
        if(items.size() > maxResults) {
            JSONHelper.putValue(result, KEY_HAS_MORE, true);
            items = items.subList(0, maxResults);
        }

        JSONArray itemArray = new JSONArray();

        for (SearchResultItem sri : items) {
            itemArray.put(sri.toJSON());
        }
        JSONHelper.putValue(result, KEY_TOTAL_COUNT, items.size());
        JSONHelper.putValue(result, KEY_LOCATIONS, itemArray);

        JSONArray methodArray = new JSONArray();
        for(String channelId : sc.getChannels()) {
            methodArray.put(JSONHelper.createJSONObject(channelId, !query.findResult(channelId).isQueryFailed()));
        }
        JSONHelper.putValue(result, KEY_METHODS, methodArray);
        return result;
    }
}

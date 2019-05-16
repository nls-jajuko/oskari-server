package fi.nls.oskari.control.statistics.plugins.unsd;

import fi.nls.oskari.control.statistics.data.*;
import fi.nls.oskari.control.statistics.plugins.StatisticalDatasourcePlugin;
import fi.nls.oskari.control.statistics.plugins.db.DatasourceLayer;
import fi.nls.oskari.control.statistics.plugins.db.StatisticalDatasource;
import fi.nls.oskari.control.statistics.plugins.unsd.parser.RegionMapper;
import fi.nls.oskari.control.statistics.plugins.unsd.parser.UnsdParser;
import fi.nls.oskari.control.statistics.plugins.unsd.requests.UnsdRequest;
import fi.nls.oskari.log.LogFactory;
import fi.nls.oskari.log.Logger;
import fi.nls.oskari.util.JSONHelper;
import org.json.JSONArray;
import org.json.JSONException;

import java.util.*;
import java.util.stream.Collectors;

public class UnsdStatisticalDatasourcePlugin extends StatisticalDatasourcePlugin {
    private final static Logger LOG = LogFactory.getLogger(UnsdStatisticalDatasourcePlugin.class);

    private UnsdConfig config;
    private UnsdParser parser;
    private UnsdIndicatorValuesFetcher indicatorValuesFetcher;
    private RegionMapper regionMapper;

    /**
     * Maps the UNSD area codes to Oskari layers.
     */
    private Map<Long, String[]> layerAreaCodes = new HashMap<>();

    @Override
    public void update() {
        // get the indicator listing
        UnsdRequest request = new UnsdRequest(config);
        request.setGoal(config.getGoal());
        String targetsResponse = request.getTargets();
        List<StatisticalIndicator> indicators = parser.parseIndicators(targetsResponse);

        // all indicators under goal have same dimensions
        String dimensions = request.getDimensions();
        for (StatisticalIndicator ind : indicators) {
            request.setIndicator(ind.getId());
            // we parse it multiple times to make copies
            ind.setDataModel(parser.parseDimensions(dimensions));
            ind.setSource(parser.parseSource(request.getIndicatorData(null)));
            getSource().getLayers().stream().forEach(l -> ind.addLayer(l));
            onIndicatorProcessed(ind);
        }
        LOG.info("Parsed indicator response.");
    }

    @Override
    public void init(StatisticalDatasource source) {
        super.init(source);
        config = new UnsdConfig(source.getConfigJSON(), source.getId());
        parser = new UnsdParser();
        indicatorValuesFetcher = new UnsdIndicatorValuesFetcher();
        indicatorValuesFetcher.init(config);
        regionMapper = new RegionMapper();
        // optimization for getting data just for the countries we are showing
        initAreaCodes(source.getLayers());
    }

    private void initAreaCodes (List<DatasourceLayer> layers)  {
        // TODO; Get codes from RegionSetHelper
        // RegionSet - layerId
        // RegionSetHelper - RegionSet
        String[] ALPHA_2_REGION_CODES = new String[]{
                "CA",
                "NO",
                "US",
                "GL",
                "DK",
                "SE",
                "IS",
                "FI"
        };
        List<String> countries = Arrays.stream(ALPHA_2_REGION_CODES)
                .map(code -> regionMapper.find(code))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .map(c -> c.m49).collect(Collectors.toList());

        for (DatasourceLayer layer : layers) {
            layerAreaCodes.put(layer.getMaplayerId(), countries.toArray(new String[0]));
        }
    }

    @Override
    public Map<String, IndicatorValue> getIndicatorValues(
            StatisticalIndicator indicator,
            StatisticalIndicatorDataModel params,
            StatisticalIndicatorLayer regionset) {

        String[] areaCodes = layerAreaCodes.get(regionset.getOskariLayerId());
        // FIXME: map codes back to region ids before returning
        Map<String, IndicatorValue> values = indicatorValuesFetcher.get(params, indicator.getId(), areaCodes);
        List<CountryRegion> regions = values.keySet().stream().map(m49 -> regionMapper.find(m49))
                .filter(Optional::isPresent)
                .map(Optional::get).collect(Collectors.toList());
        Map<String, IndicatorValue> updated = new HashMap<>();
                regions.stream().forEach( c -> {
            IndicatorValue value = values.get(Integer.toString(c.m49woleadingZeroes));
            // FIXME: check if the region code from layer is iso2 or iso3 or m49
            updated.put(c.iso2, value);
        });

        return updated;
    }
}

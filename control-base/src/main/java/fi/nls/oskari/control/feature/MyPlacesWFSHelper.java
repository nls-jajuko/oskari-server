package fi.nls.oskari.control.feature;

import java.util.Arrays;

import org.geotools.factory.CommonFactoryFinder;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.referencing.CRS;
import org.opengis.filter.Filter;
import org.opengis.filter.FilterFactory;
import org.opengis.filter.expression.Expression;

import fi.nls.oskari.domain.map.OskariLayer;
import fi.nls.oskari.util.PropertyUtil;

public class MyPlacesWFSHelper {

    public static final String PROP_MYPLACES_BASELAYER_ID = "myplaces.baselayer.id";

    private static final String PREFIX_MYPLACES = "myplaces_";
    private static final String MYPLACES_ATTR_GEOMETRY = "oskari:geometry";
    private static final String MYPLACES_ATTR_CATEGORY_ID = "oskari:category_id";
    private static final String MYPLACES_ATTR_UUID = "oskari:uuid";
    private static final String MYPLACES_ATTR_PUBLISHER_NAME = "oskari:publisher_name";

    private FilterFactory ff;
    private int myPlacesLayerId;

    public MyPlacesWFSHelper() {
        init();
    }

    public void init() {
        this.ff = CommonFactoryFinder.getFilterFactory();
        this.myPlacesLayerId = PropertyUtil.getOptional(PROP_MYPLACES_BASELAYER_ID, -2);
    }

    public int getMyPlacesLayerId() {
        return myPlacesLayerId;
    }

    public boolean isMyPlacesLayer(OskariLayer layer) {
        return layer.getId() == myPlacesLayerId;
    }

    public boolean isMyPlacesLayer(String layerId) {
        return layerId.startsWith(PREFIX_MYPLACES);
    }

    public int getCategoryId(String layerId) {
        return Integer.parseInt(layerId.substring(PREFIX_MYPLACES.length()));
    }

    public Filter getFilter(int categoryId, String uuid, ReferencedEnvelope bbox) {
        Expression _categoryId = ff.property(MYPLACES_ATTR_CATEGORY_ID);
        Expression _uuid = ff.property(MYPLACES_ATTR_UUID);

        Filter categoryIdEquals = ff.equals(_categoryId, ff.literal(categoryId));

        Filter uuidEquals = ff.equals(_uuid, ff.literal(uuid));

/*
// FIXME: Referencing publisher name requires the layer is oskari:my_places_categories instead of oskari:my_places
// which brings more attributes that we want AND breaks transport
// TODO: We might need to check if the use has right to view the layer that is not his/her own in another way
// Leaving this logic out means that guests won't see the published user content layer
        Expression _publisherName = ff.property(MYPLACES_ATTR_PUBLISHER_NAME);
        Filter publisherNameNotNull = ff.not(ff.isNull(_publisherName));
        Filter publisherNameNotEmpty = ff.notEqual(_publisherName, ff.literal(""));
        Filter publisherNameNotNullNotEmpty = ff.and(publisherNameNotNull, publisherNameNotEmpty);
        Filter uuidEqualsOrPublished = ff.or(uuidEquals, publisherNameNotNullNotEmpty);
*/
        Filter uuidEqualsOrPublished = uuidEquals;

        Filter bboxFilter = ff.bbox(MYPLACES_ATTR_GEOMETRY,
                bbox.getMinX(), bbox.getMinY(),
                bbox.getMaxX(), bbox.getMaxY(),
                CRS.toSRS(bbox.getCoordinateReferenceSystem()));

        return ff.and(Arrays.asList(categoryIdEquals, uuidEqualsOrPublished, bboxFilter));
    }

}

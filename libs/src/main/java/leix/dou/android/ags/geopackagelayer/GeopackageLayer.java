package leix.dou.android.ags.geopackagelayer;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.text.TextUtils;

import com.esri.android.map.GraphicsLayer;
import com.esri.core.geodatabase.Geopackage;
import com.esri.core.geodatabase.GeopackageFeature;
import com.esri.core.geodatabase.GeopackageFeatureTable;
import com.esri.core.geometry.Envelope;
import com.esri.core.geometry.Geometry;
import com.esri.core.geometry.SpatialReference;
import com.esri.core.map.CallbackListener;
import com.esri.core.map.Feature;
import com.esri.core.map.FeatureResult;
import com.esri.core.map.Field;
import com.esri.core.map.Graphic;
import com.esri.core.symbol.Symbol;
import com.esri.core.table.TableException;
import com.esri.core.tasks.query.QueryParameters;

import java.io.FileNotFoundException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Name:GeopackageLayer
 * Description:
 * Author: Leix
 * Date: 2017/2/25
 */
public class GeopackageLayer extends GraphicsLayer {
    final static String FEATUREID_FIELD_NAME = "OBJECTID";
    final static String SQL_QUERY_SPATIALREFERENCE = "SELECT definition FROM gpkg_spatial_ref_sys WHERE srs_id=(SELECT srs_id FROM gpkg_contents WHERE table_name=?)";

    long layerId;//layer id
    String layerName;//layer name

    String gpkgPath;//geopackage filepath
    String tableName;//geopacakge talbe name

    SpatialReference spatialReference;//layer's spatial refrence
    Geometry.Type geometryType;//layer's geometry type;
    Envelope layerExtent;// layer's extent
    List<Field> fieldList;//layer's fields;
    String objectIdField;//layer's feature id field name
    IGeopackageLayerLoadListener loadListener; //load listener
    Map<Long, Integer> featureId2GraphicIdMap;//a map of feature id to graphic id
    Symbol layerGraphicSymbol;

    @Override
    public SpatialReference getSpatialReference() {
        return spatialReference;
    }

    @Override
    public SpatialReference getDefaultSpatialReference() {
        return this.spatialReference;
    }

    public static GeopackageLayer make(String gpkgPath, String tableName) {
        return make(gpkgPath, tableName, null);
    }

    public static GeopackageLayer make(String gpkgPath, String tableName, IGeopackageLayerLoadListener listener) {
        return new GeopackageLayer(gpkgPath, tableName, listener);
    }

    GeopackageLayer(String gpkgPath, String tableName, IGeopackageLayerLoadListener listener) {
        this.gpkgPath = gpkgPath;
        this.tableName = tableName;
        this.loadListener = listener;
        this.spatialReference = getLayerSpatialReference(this.gpkgPath, this.tableName, this.loadListener);
        setDefaultSpatialReference(this.spatialReference);
        initLayerArgs(this.gpkgPath, this.tableName, this.loadListener);
        loadGraphics(this.gpkgPath, this.tableName, this.loadListener);
    }


    /**
     * get layer name
     *
     * @return layer's name
     */
    public String getLayerName() {
        return layerName;
    }

    /**
     * set this layer's name
     *
     * @param layerName layer'name
     * @return this layer
     */
    public GeopackageLayer setLayerName(String layerName) {
        this.layerName = layerName;
        return this;
    }

    /**
     * get this layer's id
     *
     * @return layer's id
     */
    public long getLayerId() {
        return layerId;
    }

    /**
     * set this layer's id
     *
     * @param layerId layer's id
     * @return this layer
     */
    public GeopackageLayer setLayerId(long layerId) {
        this.layerId = layerId;
        return this;
    }

    /**
     * get this layer's symbol of graphic
     *
     * @return
     */
    public Symbol getLayerGraphicSymbol() {
        return layerGraphicSymbol;
    }

    /**
     * set this layer's symbol of graphic
     *
     * @param layerGraphicSymbol symbol
     * @return this layer
     */
    public GeopackageLayer setLayerGraphicSymbol(Symbol layerGraphicSymbol) {
        this.layerGraphicSymbol = layerGraphicSymbol;
        return this;
    }

    /**
     * get layer's geometry type
     *
     * @return
     */
    public Geometry.Type getGeometryType() {
        return geometryType;
    }

    /**
     * get layer's extent
     *
     * @return
     */
    public Envelope getLayerExtent() {
        return layerExtent;
    }

    public List<Field> getFieldList() {
        return fieldList;
    }

    /**
     * get layer's featre id field
     *
     * @return
     */
    public String getObjectIdField() {
        return objectIdField;
    }

    /**
     * add a new feature to this layer
     *
     * @param geometry   feature's shape
     * @param attributes feature's attributes
     * @return feature's id
     */
    public long createNewFeature(Geometry geometry, Map<String, Object> attributes) throws FileNotFoundException, TableException {
        Geopackage geopackage = null;
        long featureId = -1l;
        try {
            geopackage = new Geopackage(gpkgPath);
            GeopackageFeatureTable table = geopackage.getGeopackageFeatureTable(tableName);
            if (attributes != null && attributes.containsKey(FEATUREID_FIELD_NAME))
                attributes.remove(FEATUREID_FIELD_NAME);
            GeopackageFeature feature = table.createNewFeature(attributes, geometry);
            featureId = table.addFeature(feature);

            if (featureId > -1) {
                Graphic graphic = new Graphic(geometry, layerGraphicSymbol, attributes);
                int graphicId = addGraphic(graphic);
                featureId2GraphicIdMap.put(featureId, graphicId);
            }
        } finally {
            if (geopackage != null)
                geopackage.dispose();
        }
        return featureId;
    }

    /**
     * add a new feature to this layer
     *
     * @param feature new feature
     * @return feature's id
     */
    public long createNewFeature(Feature feature) throws FileNotFoundException, TableException {
        Geometry geometry = feature == null ? null : feature.getGeometry();
        Map<String, Object> attributes = feature == null ? (Map<String, Object>) null : feature.getAttributes();
        return createNewFeature(geometry, attributes);
    }

    /**
     * update feature's shape
     *
     * @param featureId feature id
     * @param geometry  feature's new shape
     * @return feature id
     */
    public long updateFeatureShape(long featureId, Geometry geometry) throws FileNotFoundException, TableException {
        Geopackage geopackage = null;
        try {
            geopackage = new Geopackage(gpkgPath);
            GeopackageFeatureTable table = geopackage.getGeopackageFeatureTable(tableName);
            table.updateFeature(featureId, geometry);

            if (featureId > -1 && featureId2GraphicIdMap.containsKey(featureId)) {
                int graphicId = featureId2GraphicIdMap.get(featureId);
                updateGraphic(graphicId, geometry);
            }
        } finally {
            if (geopackage != null)
                geopackage.dispose();
        }
        return featureId;
    }

    /**
     * update feature's attributes
     *
     * @param featureId  feature id
     * @param attributes new attributes
     * @return feature's id
     * @throws FileNotFoundException can not find the geopackage file
     * @throws TableException        s
     */
    public long updateFeatureAttributes(long featureId, Map<String, Object> attributes) throws FileNotFoundException, TableException {
        Geopackage geopackage = null;
        try {
            geopackage = new Geopackage(gpkgPath);
            GeopackageFeatureTable table = geopackage.getGeopackageFeatureTable(tableName);
            if (attributes != null && attributes.containsKey(FEATUREID_FIELD_NAME))
                attributes.remove(FEATUREID_FIELD_NAME);
            table.updateFeature(featureId, attributes);

            if (featureId > -1 && featureId2GraphicIdMap.containsKey(featureId)) {
                int graphicId = featureId2GraphicIdMap.get(featureId);
                updateGraphic(graphicId, attributes);
            }
        } finally {
            if (geopackage != null)
                geopackage.dispose();
        }
        return featureId;
    }

    /**
     * update feature
     *
     * @param featureId  feature's id
     * @param geometry   feature's shape
     * @param attributes feature's attributes
     * @return feature's id
     * @throws FileNotFoundException
     * @throws TableException
     */
    public long updateFeature(long featureId, Geometry geometry, Map<String, Object> attributes) throws FileNotFoundException, TableException {
        updateFeatureShape(featureId, geometry);
        updateFeatureAttributes(featureId, attributes);
        return featureId;
    }

    /**
     * update feature
     *
     * @param feature feature
     * @return
     * @throws FileNotFoundException
     * @throws TableException
     */
    public long updateFeature(Feature feature) throws FileNotFoundException, TableException {
        Geopackage geopackage = null;
        long featureId = -1;
        try {
            if (feature != null) {
                Map<String, Object> attributes = feature.getAttributes();
                if (attributes != null && attributes.containsKey(FEATUREID_FIELD_NAME)) {
                    featureId = (long) attributes.get(FEATUREID_FIELD_NAME);
                    if (featureId > -1) {
                        geopackage = new Geopackage(gpkgPath);
                        GeopackageFeatureTable table = geopackage.getGeopackageFeatureTable(tableName);
                        Geometry geometry = feature.getGeometry();
                        table.updateFeature(featureId, attributes, geometry);

                        if (featureId2GraphicIdMap.containsKey(featureId)) {
                            int graphicId = featureId2GraphicIdMap.get(featureId);
                            Graphic graphic = new Graphic(geometry, layerGraphicSymbol, attributes);
                            updateGraphic(graphicId, graphic);
                        }
                    }
                }
            }
            return featureId;
        } finally {
            if (geopackage != null) geopackage.dispose();
        }
    }

    /**
     * delete feature
     *
     * @param featureId feature id
     * @return success
     * @throws FileNotFoundException
     */
    public boolean deleteFeature(long featureId) throws FileNotFoundException {
        Geopackage geopackage = null;
        try {
            geopackage = new Geopackage(gpkgPath);
            GeopackageFeatureTable table = geopackage.getGeopackageFeatureTable(tableName);
            table.deleteFeature(featureId);

            if(featureId2GraphicIdMap.containsKey(featureId)){
                int gid=featureId2GraphicIdMap.get(featureId);
                removeGraphic(gid);
                featureId2GraphicIdMap.remove(featureId);
            }
            return true;
        } finally {
            if (geopackage != null) geopackage.dispose();
        }
    }

    /**
     * delete a feature form this layer
     *
     * @param feature delete feature
     * @return success
     * @throws FileNotFoundException geopakcage file not found
     */
    public boolean deleteFeature(Feature feature) throws FileNotFoundException {
        Geopackage geopackage = null;
        try {
            if (feature != null) {
                Map<String, Object> attributes = feature.getAttributes();
                if (attributes != null && attributes.containsKey(FEATUREID_FIELD_NAME)) {
                    long featureId = (long) attributes.get(FEATUREID_FIELD_NAME);
                    geopackage = new Geopackage(gpkgPath);
                    GeopackageFeatureTable table = geopackage.getGeopackageFeatureTable(tableName);
                    table.deleteFeature(featureId);

                    if (featureId2GraphicIdMap.containsKey(featureId)) {
                        int gid = featureId2GraphicIdMap.get(featureId);
                        removeGraphic(gid);
                        featureId2GraphicIdMap.remove(featureId);
                    }
                    return true;
                }
            }
        } finally {
            if (geopackage != null) geopackage.dispose();
        }
        return false;
    }

    /**
     * delete features
     *
     * @param featureIds
     * @return success
     * @throws FileNotFoundException geopackage file not found
     */
    public boolean deleteFeatures(long[] featureIds) throws FileNotFoundException {
        Geopackage geopackage = null;
        if (featureIds == null || featureIds.length < 1) return false;//throw a error
        try {
            geopackage = new Geopackage(gpkgPath);
            GeopackageFeatureTable table = geopackage.getGeopackageFeatureTable(tableName);
            table.deleteFeatures(featureIds);
            for (long fid : featureIds) {
                if (featureId2GraphicIdMap.containsKey(fid)) {
                    int gid = featureId2GraphicIdMap.get(fid);
                    removeGraphic(gid);
                    featureId2GraphicIdMap.remove(fid);
                }
            }
            return true;
        } finally {
            geopackage.dispose();
        }
    }


    /**
     * init layer's arguments
     *
     * @param path     geopackage's path
     * @param name     feature table name
     * @param listener load listner
     */
    private void initLayerArgs(String path, String name, IGeopackageLayerLoadListener listener) {
        Geopackage geopackage = null;
        try {
            geopackage = new Geopackage(path);
            GeopackageFeatureTable table = geopackage.getGeopackageFeatureTable(name);
            this.geometryType = table.getGeometryType();
            this.layerExtent = table.getExtent();
            this.fieldList = table.getFields();
            this.objectIdField = table.getObjectIdField();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            if (listener != null) listener.onError(GeopackageLayer.this, e);
        } finally {
            geopackage.dispose();
        }
    }

    /**
     * load graphics to this layer
     *
     * @param path
     * @param name
     * @throws FileNotFoundException
     */
    protected void loadGraphics(String path, String name, final IGeopackageLayerLoadListener listener) {
        try {
            final Geopackage geopackage = new Geopackage(path);
            GeopackageFeatureTable table = geopackage.getGeopackageFeatureTable(name);
            table.queryFeatures(new QueryParameters(), new CallbackListener<FeatureResult>() {
                @Override
                public void onCallback(FeatureResult objects) {
                    geopackage.dispose();
                    featureId2GraphicIdMap = new HashMap<Long, Integer>();
                    for (Object obj : objects) {
                        Feature feature = (Feature) obj;
                        long featureId = feature.getId();
                        Geometry shape = feature.getGeometry();
                        Map<String, Object> attributes = feature.getAttributes();
                        Graphic graphic = new Graphic(shape, null, attributes);
                        int graphicId = GeopackageLayer.this.addGraphic(graphic);
                        featureId2GraphicIdMap.put(featureId, graphicId);
                    }
                    if (listener != null) listener.onFinish(GeopackageLayer.this);
                }

                @Override
                public void onError(Throwable throwable) {
                    geopackage.dispose();
                    throwable.printStackTrace();
                    if (listener != null) {
                        listener.onError(GeopackageLayer.this, throwable);
                    }
                }
            });
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            if (listener != null)
                listener.onError(GeopackageLayer.this, e);
        }
    }

    /**
     * get this layer's spatial reference from *.gpkg file
     *
     * @param path gpkg file path
     * @param name feature table name
     * @return spatial refrence
     */
    protected SpatialReference getLayerSpatialReference(String path, String name, IGeopackageLayerLoadListener listener) {
        SQLiteDatabase database = null;
        Cursor cursor = null;
        try {
            database = SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.OPEN_READWRITE);
            cursor = database.rawQuery(SQL_QUERY_SPATIALREFERENCE, new String[]{name});
            if (cursor != null && cursor.getCount() > 0 && cursor.moveToFirst()) {
                String text = cursor.getString(0);
                if (!TextUtils.isEmpty(text))
                    return SpatialReference.create(text);
            }
        } catch (Exception e) {
            e.printStackTrace();
            if (listener != null) listener.onError(GeopackageLayer.this, e);
        } finally {
            if (database != null)
                database.close();
            if (cursor != null)
                cursor.close();
        }
        return null;
    }
}

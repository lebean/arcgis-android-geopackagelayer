package leix.dou.android.ags.geopackagelayer;

/**
 * Name:
 * Description:
 * Author: Leix
 * Date: 2017/2/25
 */
public interface IGeopackageLayerLoadListener {
    void onError(GeopackageLayer layer, Throwable e);
    void onFinish(GeopackageLayer layer);
}

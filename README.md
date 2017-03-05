# ArcGISGeopackageLayer
对ArcGIS for Android SDK中的Geopackage进行扩展，使其支持WGS84以外的坐标系统

##使用方法：

###1）初始化GeopackageLayer
~~~java
String geopackagePath="";
String geopackageTableName="";
GeopackageLyaer layer=GeopackageLayer.make(geopackagePath,geopackageTableName);
~~~
###2) 添加图层到地图
~~~java
MapView map=(MapView)findViewById(R.id.map);
String geopackagePath="";
String geopackageTableName="";
GeopackageLyaer layer=GeopackageLayer.make(geopackagePath,geopackageTableName);
map.addLayer(layer);
~~~
###3)添加新要
~~~java
Geometry shape=new Polygon();
Map<String,Object> attributes=new HashMap<>();
long featureId=layer.createNewFeature(shap,attributes);
~~~

###4)修改要素
~~~java
Geometry shape=new Polygon();
Map<String,Object> attributes=new HashMap<>();
long featureId=23;
layer.updateFeature(featureId,shape,attributes);
~~~

###5)删除要素
~~~java
long featureId=34;
long[] featureIdArray=new long[]{23,45,76,98};
layer.deleteFeature(featureId);
layer.deleteFeatures(featureIdArray);
~~~

#各位新，要是觉得对你有用的话，给我点个星星什么的啊，你们的星星是我前进的动力。。
##That's all

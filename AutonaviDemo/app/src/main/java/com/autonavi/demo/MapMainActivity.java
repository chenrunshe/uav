package com.autonavi.demo;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Application;
import android.app.DirectAction;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.IBinder;
import android.os.PersistableBundle;
import android.os.RemoteException;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.amap.api.location.AMapLocation;
import com.amap.api.location.AMapLocationClient;
import com.amap.api.location.AMapLocationClientOption;
import com.amap.api.location.AMapLocationListener;
import com.amap.api.maps.AMap;
import com.amap.api.maps.CameraUpdateFactory;
import com.amap.api.maps.LocationSource;
import com.amap.api.maps.MapView;
import com.amap.api.maps.model.BitmapDescriptor;
import com.amap.api.maps.model.BitmapDescriptorFactory;
import com.amap.api.maps.model.CircleOptions;
import com.amap.api.maps.model.LatLng;
import com.amap.api.maps.model.LatLngBounds;
import com.amap.api.maps.model.Marker;
import com.amap.api.maps.model.MarkerOptions;
import com.amap.api.maps.model.MyLocationStyle;
import com.amap.api.services.core.AMapException;
import com.amap.api.services.core.LatLonPoint;
import com.amap.api.services.core.PoiItem;
import com.amap.api.services.core.SuggestionCity;
import com.amap.api.services.poisearch.PoiResult;
import com.amap.api.services.poisearch.PoiSearch;
import com.example.bt_com.SDK;
import com.example.lanyatongxin.aidl.DataCallback;
import com.example.lanyatongxin.aidl.DataInterface;
import com.google.gson.Gson;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class MapMainActivity extends Activity implements View.OnClickListener,
        AMap.OnMapClickListener, AMap.InfoWindowAdapter, AMap.OnMarkerClickListener,
        PoiSearch.OnPoiSearchListener, LocationSource, AMapLocationListener {

    private final static String TAG = "MapMainActivity";

    private MapView mapview;
    private AMap mAMap;
    private PoiResult poiResult; // poi???????????????
    private int currentPage = 0;// ??????????????????0????????????
    private PoiSearch.Query query;// Poi???????????????
    private LatLonPoint lp = new LatLonPoint(39.993167, 116.473274);//
    private Marker locationMarker; // ????????????
    private Marker detailMarker;
    private Marker mlastMarker;
    private PoiSearch poiSearch;
    private myPoiOverlay poiOverlay;// poi??????
    private List<PoiItem> poiItems;// poi??????

    private TextView mPoiName, mPoiAddress;
    private String keyWord = "";
    private String city = "?????????";
    private EditText mSearchText;

    private boolean isLocationSuccess;
    private MyLocationStyle myLocationStyle;
    private OnLocationChangedListener mListener;
    private AMapLocationClient mlocationClient;
    private AMapLocationClientOption mLocationOption;
    //????????????????????????????????????????????????true???????????????????????????????????????????????????????????????
    private boolean needCheckBackLocation = false;
    //???????????????target > 28????????????????????????????????????????????????"????????????"???????????????
    private static String BACK_LOCATION_PERMISSION = "android.permission.ACCESS_BACKGROUND_LOCATION";


    @Override
    public void onGetDirectActions(@NonNull CancellationSignal cancellationSignal, @NonNull Consumer<List<DirectAction>> callback) {
        super.onGetDirectActions(cancellationSignal, callback);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.poiaroundsearch_activity);
        mapview = (MapView)findViewById(R.id.mapView);
        mapview.onCreate(savedInstanceState);
        init();
        isLocationSuccess = false;
        findViewById(R.id.startOfflineActivity).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //startOfflineActivity();
                getID();
            }
        });
        //initSDK(getApplication());
        bindService();
        sdkListener();
        //getID();

    }

    @Override
    public void onPerformDirectAction(@NonNull String actionId, @NonNull Bundle arguments, @NonNull CancellationSignal cancellationSignal, @NonNull Consumer<Bundle> resultListener) {
        super.onPerformDirectAction(actionId, arguments, cancellationSignal, resultListener);
    }

    private AMapLocation tempAmapLocation=null;

    /**
     * ???????????????????????????
     */
    @Override
    public void onLocationChanged(AMapLocation amapLocation) {
        if (mListener != null && amapLocation != null) {
            if (amapLocation != null
                    && amapLocation.getErrorCode() == 0) {
                mListener.onLocationChanged(amapLocation);// ?????????????????????
                city = amapLocation.getCity();
                lp = new LatLonPoint(amapLocation.getLatitude(), amapLocation.getLongitude());
                keyWord = "?????????";
                isLocationSuccess = true;
                //mAMap.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(lp.getLatitude(), lp.getLongitude()), 14));
                Log.d(TAG, "Location: " + amapLocation.getCity());
            } else {
                String errText = "????????????," + amapLocation.getErrorCode()+ ": " + amapLocation.getErrorInfo();
                Log.e("AmapErr",errText);
            }
            tempAmapLocation=amapLocation;
        }
    }

    /**
     * ????????????
     */
    @Override
    public void activate(OnLocationChangedListener listener) {
        mListener = listener;
        Log.d(TAG, "activate");
        if (mlocationClient == null) {
            mlocationClient = new AMapLocationClient(this);
            mLocationOption = new AMapLocationClientOption();
            //??????????????????
            mlocationClient.setLocationListener(this);
            //??????????????????????????????
            mLocationOption.setLocationMode(AMapLocationClientOption.AMapLocationMode.Hight_Accuracy);
            //??????????????????
            mlocationClient.setLocationOption(mLocationOption);
            // ????????????????????????????????????????????????????????????????????????????????????????????????????????????
            // ??????????????????????????????????????????????????????????????????2000ms?????????????????????????????????stopLocation()???????????????????????????
            // ???????????????????????????????????????????????????onDestroy()??????
            // ?????????????????????????????????????????????????????????????????????stopLocation()???????????????????????????sdk???????????????
            mlocationClient.startLocation();
            Log.d(TAG, "activate-startLocation");
        }
    }

    /**
     * ????????????
     */
    @Override
    public void deactivate() {
        mListener = null;
        if (mlocationClient != null) {
            mlocationClient.stopLocation();
            mlocationClient.onDestroy();
        }
        mlocationClient = null;
    }


    /**
     * ?????????AMap??????
     */
    private void init() {
        if (mAMap == null) {
            mAMap = mapview.getMap();
            setUpMap();
            mAMap.setOnMapClickListener(this);
            mAMap.setOnMarkerClickListener(this);
            mAMap.setInfoWindowAdapter(this);
            mAMap.setMapType(AMap.MAP_TYPE_SATELLITE);
            TextView searchButton = (TextView) findViewById(R.id.btn_search);
            searchButton.setOnClickListener(this);
            locationMarker = mAMap.addMarker(new MarkerOptions()
                    .anchor(0.5f, 0.5f)
                    .icon(BitmapDescriptorFactory
                            .fromBitmap(BitmapFactory.decodeResource(getResources(), R.drawable.point4)))
                    .position(new LatLng(lp.getLatitude(), lp.getLongitude())));

        }
        //mAMap.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(lp.getLatitude(), lp.getLongitude()), 14));
    }

    /**
     * ????????????poi??????
     */
    /**
     * ????????????poi??????
     */
    protected void doSearchQuery() {
        Log.d(TAG,"doSearchQuery");
        currentPage = 0;
        query = new PoiSearch.Query(keyWord, "", city);// ????????????????????????????????????????????????????????????poi????????????????????????????????????poi??????????????????????????????????????????
        query.setPageSize(20);// ?????????????????????????????????poiitem
        query.setPageNum(currentPage);// ??????????????????

        if (lp != null) {
            poiSearch = new PoiSearch(this, query);
            poiSearch.setOnPoiSearchListener(this);
            poiSearch.setBound(new PoiSearch.SearchBound(lp, 5000, true));//
            // ????????????????????????lp????????????????????????5000?????????
            poiSearch.searchPOIAsyn();// ????????????
            count++;
        }
    }

    /**
     * ??????????????????
     */
    @Override
    protected void onResume() {
        super.onResume();
        if (Build.VERSION.SDK_INT >= 23) {
            if (isNeedCheck) {
                checkPermissions(needPermissions);
            }
        }
        mapview.onResume();


        new Thread(new Runnable() {
            @Override
            public void run() {
                while(true){
                    if(isLocationSuccess)  doSearchQuery();
                    Log.d(TAG, "loop");
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    if(count >= 0) break;
                }
            }
        }).start();



    }

    private int count = -1;

    /**
     * ??????????????????
     */
    @Override
    protected void onPause() {
        super.onPause();
        mapview.onPause();
        deactivate();
    }

    /**
     * ??????????????????
     */
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        mapview.onSaveInstanceState(outState);
    }

    /**
     * ??????????????????
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        mapview.onDestroy();
    }

    @Override
    public void onPoiItemSearched(PoiItem poiitem, int rcode) {

    }


    @Override
    public void onPoiSearched(PoiResult result, int rcode) {
        if (rcode == AMapException.CODE_AMAP_SUCCESS) {
            if (result != null && result.getQuery() != null) {// ??????poi?????????
                if (result.getQuery().equals(query)) {// ??????????????????
                    poiResult = result;
                    poiItems = poiResult.getPois();// ??????????????????poiitem????????????????????????0??????
                    List<SuggestionCity> suggestionCities = poiResult
                            .getSearchSuggestionCitys();// ???????????????poiitem?????????????????????????????????????????????????????????
                    if (poiItems != null && poiItems.size() > 0) {
                        //??????POI????????????

                        //???????????????marker??????
                        if (mlastMarker != null) {
                            resetlastmarker();
                        }
                        //???????????????????????????marker
                        if (poiOverlay !=null) {
                            poiOverlay.removeFromMap();
                        }
                        mAMap.clear();
                        poiOverlay = new myPoiOverlay(mAMap, poiItems);
                        poiOverlay.addToMap();
                        poiOverlay.zoomToSpan();

                        mAMap.addMarker(new MarkerOptions()
                                .anchor(0.5f, 0.5f)
                                .icon(BitmapDescriptorFactory
                                        .fromBitmap(BitmapFactory.decodeResource(
                                                getResources(), R.drawable.point4)))
                                .position(new LatLng(lp.getLatitude(), lp.getLongitude())));

                        mAMap.addCircle(new CircleOptions()
                                .center(new LatLng(lp.getLatitude(),
                                        lp.getLongitude())).radius(5000)
                                .strokeColor(Color.BLUE)
                                .fillColor(Color.argb(50, 1, 1, 1))
                                .strokeWidth(2));
                    } else if (suggestionCities != null
                            && suggestionCities.size() > 0) {
                        showSuggestCity(suggestionCities);
                    } else {
                        /*ToastUtil.show(this.getApplicationContext(),
                                R.string.no_result);*/
                    }
                }
            } else {
                /*ToastUtil
                        .show(this.getApplicationContext(), R.string.no_result);*/
            }
        } else {
            /*ToastUtil
                    .showerror(this.getApplicationContext(), rcode);*/
        }
    }


    @Override
    public boolean onMarkerClick(Marker marker) {

        if (marker.getObject() != null) {

            try {
                PoiItem mCurrentPoi = (PoiItem) marker.getObject();
                if (mlastMarker == null) {
                    mlastMarker = marker;
                } else {
                    // ?????????????????????marker?????????????????????
                    resetlastmarker();
                    mlastMarker = marker;
                }
                detailMarker = marker;
                detailMarker.setIcon(BitmapDescriptorFactory
                        .fromBitmap(BitmapFactory.decodeResource(
                                getResources(),
                                R.drawable.poi_marker_pressed)));
                setPoiItemDisplayContent(mCurrentPoi);
            } catch (Exception e) {
                // TODO: handle exception
            }
        }else {
            resetlastmarker();
        }
        return true;
    }

    // ?????????????????????marker?????????????????????
    private void resetlastmarker() {
        int index = poiOverlay.getPoiIndex(mlastMarker);
        if (index < 10) {
            mlastMarker.setIcon(BitmapDescriptorFactory
                    .fromBitmap(BitmapFactory.decodeResource(
                            getResources(),
                            markers[index])));
        }else {
            mlastMarker.setIcon(BitmapDescriptorFactory.fromBitmap(
                    BitmapFactory.decodeResource(getResources(), R.drawable.marker_other_highlight)));
        }
        mlastMarker = null;

    }


    private void setPoiItemDisplayContent(final PoiItem mCurrentPoi) {
        mPoiName.setText(mCurrentPoi.getTitle());
        mPoiAddress.setText(mCurrentPoi.getSnippet());
    }

    @Override
    public View getInfoContents(Marker arg0) {
        return null;
    }

    @Override
    public View getInfoWindow(Marker arg0) {
        return null;
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btn_search:
                keyWord = mSearchText.getText().toString().trim();
                if ("".equals(keyWord)) {
                    //ToastUtil.show(PoiAroundSearchActivity.this, "????????????????????????");
                    return;
                } else {
                    doSearchQuery();
                }
                break;
            default:
                break;
        }

    }

    private int[] markers = {R.drawable.poi_marker_1,
            R.drawable.poi_marker_2,
            R.drawable.poi_marker_3,
            R.drawable.poi_marker_4,
            R.drawable.poi_marker_5,
            R.drawable.poi_marker_6,
            R.drawable.poi_marker_7,
            R.drawable.poi_marker_8,
            R.drawable.poi_marker_9,
            R.drawable.poi_marker_10
    };

    @Override
    public void onMapClick(LatLng arg0) {
        if (mlastMarker != null) {
            resetlastmarker();
        }

    }
    /**
     * poi?????????????????????????????????????????????????????????
     */
    private void showSuggestCity(List<SuggestionCity> cities) {
        String infomation = "????????????\n";
        for (int i = 0; i < cities.size(); i++) {
            infomation += "????????????:" + cities.get(i).getCityName() + "????????????:"
                    + cities.get(i).getCityCode() + "????????????:"
                    + cities.get(i).getAdCode() + "\n";
        }
        ToastUtil.show(this, infomation);

    }

    private class myPoiOverlay {
        private AMap mamap;
        private List<PoiItem> mPois;
        private ArrayList<Marker> mPoiMarks = new ArrayList<Marker>();
        public myPoiOverlay(AMap amap ,List<PoiItem> pois) {
            mamap = amap;
            mPois = pois;
        }

        /**
         * ??????Marker???????????????
         * @since V2.1.0
         */
        public void addToMap() {
            for (int i = 0; i < mPois.size(); i++) {
                Marker marker = mamap.addMarker(getMarkerOptions(i));
                PoiItem item = mPois.get(i);
                marker.setObject(item);
                mPoiMarks.add(marker);
            }
        }

        /**
         * ??????PoiOverlay????????????Marker???
         *
         * @since V2.1.0
         */
        public void removeFromMap() {
            for (Marker mark : mPoiMarks) {
                mark.remove();
            }
        }

        /**
         * ?????????????????????????????????
         * @since V2.1.0
         */
        public void zoomToSpan() {
            if (mPois != null && mPois.size() > 0) {
                if (mamap == null)
                    return;
                LatLngBounds bounds = getLatLngBounds();
                mamap.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, 100));
            }
        }

        private LatLngBounds getLatLngBounds() {
            LatLngBounds.Builder b = LatLngBounds.builder();
            for (int i = 0; i < mPois.size(); i++) {
                b.include(new LatLng(mPois.get(i).getLatLonPoint().getLatitude(),
                        mPois.get(i).getLatLonPoint().getLongitude()));
            }
            return b.build();
        }

        private MarkerOptions getMarkerOptions(int index) {
            return new MarkerOptions()
                    .position(
                            new LatLng(mPois.get(index).getLatLonPoint()
                                    .getLatitude(), mPois.get(index)
                                    .getLatLonPoint().getLongitude()))
                    .title(getTitle(index)).snippet(getSnippet(index))
                    .icon(getBitmapDescriptor(index));
        }

        protected String getTitle(int index) {
            return mPois.get(index).getTitle();
        }

        protected String getSnippet(int index) {
            return mPois.get(index).getSnippet();
        }

        /**
         * ???marker?????????poi???list????????????
         *
         * @param marker ????????????????????????
         * @return ?????????marker?????????poi???list????????????
         * @since V2.1.0
         */
        public int getPoiIndex(Marker marker) {
            for (int i = 0; i < mPoiMarks.size(); i++) {
                if (mPoiMarks.get(i).equals(marker)) {
                    return i;
                }
            }
            return -1;
        }

        /**
         * ?????????index???poi????????????
         * @param index ?????????poi???
         * @return poi????????????poi???????????????????????????????????????????????????com.amap.api.services.core???????????? <strong><a href="../../../../../../Search/com/amap/api/services/core/PoiItem.html" title="com.amap.api.services.core?????????">PoiItem</a></strong>???
         * @since V2.1.0
         */
        public PoiItem getPoiItem(int index) {
            if (index < 0 || index >= mPois.size()) {
                return null;
            }
            return mPois.get(index);
        }

        protected BitmapDescriptor getBitmapDescriptor(int arg0) {
            if (arg0 < 10) {
                BitmapDescriptor icon = BitmapDescriptorFactory.fromBitmap(
                        BitmapFactory.decodeResource(getResources(), markers[arg0]));
                return icon;
            }else {
                BitmapDescriptor icon = BitmapDescriptorFactory.fromBitmap(
                        BitmapFactory.decodeResource(getResources(), R.drawable.marker_other_highlight));
                return icon;
            }
        }

    }

    /**
     * ????????????amap?????????
     */
    private void setUpMap() {
        // ??????????????????????????????
        MyLocationStyle myLocationStyle = new MyLocationStyle();
        myLocationStyle.myLocationIcon(BitmapDescriptorFactory
                .fromResource(R.drawable.location_marker));// ????????????????????????
        myLocationStyle.strokeColor(Color.BLACK);// ???????????????????????????
        myLocationStyle.radiusFillColor(Color.argb(100, 0, 0, 180));// ???????????????????????????
        // myLocationStyle.anchor(int,int)//????????????????????????
        myLocationStyle.strokeWidth(1.0f);// ???????????????????????????
        mAMap.setMyLocationStyle(myLocationStyle);
        mAMap.setLocationSource(this);// ??????????????????
        mAMap.getUiSettings().setMyLocationButtonEnabled(true);// ????????????????????????????????????
        mAMap.setMyLocationEnabled(true);// ?????????true??????????????????????????????????????????false??????????????????????????????????????????????????????false
        // aMap.setMyLocationType()
    }


    /*************************************** ????????????******************************************************/

    /**
     * ?????????????????????????????????
     */
    protected String[] needPermissions = {
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.READ_PHONE_STATE,
            BACK_LOCATION_PERMISSION
    };

    private static final int PERMISSON_REQUESTCODE = 0;

    /**
     * ????????????????????????????????????????????????
     */
    private boolean isNeedCheck = true;


    /**
     * @param
     * @since 2.5.0
     */
    @TargetApi(23)
    private void checkPermissions(String... permissions) {
        try{
            if (Build.VERSION.SDK_INT >= 23 && getApplicationInfo().targetSdkVersion >= 23) {
                List<String> needRequestPermissonList = findDeniedPermissions(permissions);
                if (null != needRequestPermissonList
                        && needRequestPermissonList.size() > 0) {
                    try {
                        String[] array = needRequestPermissonList.toArray(new String[needRequestPermissonList.size()]);
                        Method method = getClass().getMethod("requestPermissions", new Class[]{String[].class, int.class});
                        method.invoke(this, array, 0);
                    } catch (Throwable e) {

                    }
                }
            }

        }catch(Throwable e){
            e.printStackTrace();
        }
    }

    /**
     * ?????????????????????????????????????????????
     *
     * @param permissions
     * @return
     * @since 2.5.0
     */
    @TargetApi(23)
    private List<String> findDeniedPermissions(String[] permissions) {
        try{
            List<String> needRequestPermissonList = new ArrayList<String>();
            if (Build.VERSION.SDK_INT >= 23 && getApplicationInfo().targetSdkVersion >= 23) {
                for (String perm : permissions) {
                    if (checkMySelfPermission(perm) != PackageManager.PERMISSION_GRANTED
                            || shouldShowMyRequestPermissionRationale(perm)) {
                        if(!needCheckBackLocation
                                && BACK_LOCATION_PERMISSION.equals(perm)) {
                            continue;
                        }
                        needRequestPermissonList.add(perm);
                    }
                }
            }
            return needRequestPermissonList;
        }catch(Throwable e){
            e.printStackTrace();
        }
        return null;
    }

    private int checkMySelfPermission(String perm) {
        try {
            Method method = getClass().getMethod("checkSelfPermission", new Class[]{String.class});
            Integer permissionInt = (Integer) method.invoke(this, perm);
            return permissionInt;
        } catch (Throwable e) {
        }
        return -1;
    }

    private boolean shouldShowMyRequestPermissionRationale(String perm) {
        try {
            Method method = getClass().getMethod("shouldShowRequestPermissionRationale", new Class[]{String.class});
            Boolean permissionInt = (Boolean) method.invoke(this, perm);
            return permissionInt;
        } catch (Throwable e) {
        }
        return false;
    }

    /**
     * ???Activity????????????startActvity????????????????????????
     */
    private void startOfflineActivity(){
        startActivity(new Intent(this.getApplicationContext(),
                com.amap.api.maps.offlinemap.OfflineMapActivity.class));
    }

    private DataInterface dataInterface;

    private ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            dataInterface = DataInterface.Stub.asInterface(service);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {

        }
    };

    private void bindService(){
        Intent intent = new Intent();
        intent.setPackage("com.example.lanyatongxin.aidl");
        intent.setAction("com.example.aidldemo.action");
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState, @Nullable PersistableBundle persistentState) {
        super.onCreate(savedInstanceState, persistentState);
    }

    private void initSDK(Application application){
        SDK.getInstance().initApp(application);
    }

    private void sdkListener(){
        SDK.getInstance().initApp(getApplication(), connected -> {
            if(connected){
                Log.d(TAG, "sdkListener");
                SDK.getInstance().getDataCallBack(new DataCallback() {
                    @Override
                    public void onData(byte[] data) throws RemoteException {
                        receiveGPS(data);
                    }

                    @Override
                    public IBinder asBinder() {
                        return null;
                    }
                });
            }
        });
    }

    private void sendData(byte[] data){
        try {
            SDK.getInstance().sendData(data);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    private void getID(){
        try {
            int id = SDK.getInstance().getID();
            Toast.makeText(this, String.format("?????????ID???%s", id), Toast.LENGTH_SHORT).show();
            if(tempAmapLocation!=null)
            {
                sendGPS(id,tempAmapLocation);
            }
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    private void sendGPS(int id,AMapLocation aMapLocation){
        GeoLocation geoLocation=new GeoLocation();
        geoLocation.id=id;
        geoLocation.Latitude=aMapLocation.getLatitude();
        geoLocation.Longitude=aMapLocation.getLongitude();
        String geoLocationJson=new Gson().toJson(geoLocation);
        Log.d(TAG, "sendGPS geoLocationJson="+geoLocationJson);
        byte[] geoLocationByte=geoLocationJson.getBytes(StandardCharsets.US_ASCII);
        int length=geoLocationByte.length;
        byte[] tempData=new byte[1+1+1+2+2+length];
        byte[] data=new byte[1+1+1+2+2+length+1];
        tempData[0]=(byte)0xFE;
        data[0]=(byte)0xFE;
        tempData[1]=(byte)(1+2+2+length);
        data[1]=(byte)(1+2+2+length);
        tempData[2]=(byte)0x02;
        data[2]=(byte)0x02;
        byte[] senderIdByte=Utils.short2byte((short) id);
        tempData[3]=senderIdByte[0];
        data[3]=senderIdByte[0];
        tempData[4]=senderIdByte[1];
        data[4]=senderIdByte[1];
        tempData[5]=(byte)0xFF;
        data[5]=(byte)0xFF;
        tempData[6]=(byte)0xFF;
        data[6]=(byte)0xFF;
        for(int i=0;i<length;i++) {
            tempData[7+i]=geoLocationByte[i];
            data[7+i]=geoLocationByte[i];
        }
        data[7+length]=Utils.getXORCheck(tempData);
        Log.d(TAG, "sendDataByte="+data.toString());
        sendData(data);
    }

    private GeoLocation receiveGPS(byte[] data)
    {
        GeoLocation geoLocation=new GeoLocation();
        StringBuilder sb = new StringBuilder();
        for (byte b : data) {
            if (sb.length() > 0) {
                sb.append(" ");
            }
            sb.append(String.format("%02x", b));
        }
        runOnUiThread(() -> Toast.makeText(getApplicationContext(), String.format("???????????????%s", sb.toString()), Toast.LENGTH_SHORT).show());

        int length=data[1]-1-2-2;
        if(data[2]==0x02) {
            byte[] senderIdByte=new byte[2];
            senderIdByte[0]=data[3];
            senderIdByte[0]=data[4];
            byte[] geoLocationByte=new byte[length];
            for(int i=0;i<length;i++) {
                geoLocationByte[i]=data[7+i];
            }
            String geoLocationJson=new String(geoLocationByte,StandardCharsets.US_ASCII);
            Log.d(TAG, "receiveGPS geoLocationJson="+geoLocationJson);
            geoLocation=new Gson().fromJson(geoLocationJson,GeoLocation.class);
            geoLocation.id=(int)Utils.byte2short(senderIdByte);
        }
        return geoLocation;
    }
}

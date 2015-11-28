package com.ritave.myrevolution;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.net.nsd.NsdServiceInfo;
import android.net.wifi.WpsInfo;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceInfo;
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceRequest;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.widget.AdapterView;
import android.widget.GridView;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    static final int REQUEST_IMAGE_CAPTURE = 1;

    private File fImageDir;
    private File mCurrentPhotoFile;
    private ArrayList<ImageItem> mPhotos = new ArrayList<>();
    private GalleryViewAdapter mAdapter;
    private GridView mGridView;
    private String mImageToSend;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        fImageDir = new File(getExternalFilesDir(null), "images");
        DeleteAllFiles(fImageDir);

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                takePicture();
            }
        });

        mGridView = (GridView)findViewById(R.id.galleryView);
        mAdapter = new GalleryViewAdapter(this, R.layout.galleryview_item, mPhotos);
        mGridView.setAdapter(mAdapter);

        mGridView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                showPicture(mPhotos.get(position));
            }
        });

        initWifi();
        mSender = new ImageSender();
    }

    @Override
    public void onResume()
    {
        super.onResume();

        mWifiReceiver.Init(mManager, mChannel, this);
        registerReceiver(mWifiReceiver, mWifiIntentFilter);
    }

    @Override
    public void onPause()
    {
        super.onPause();
        unregisterReceiver(mWifiReceiver);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        if (requestCode == REQUEST_IMAGE_CAPTURE)
        {
            assert mCurrentPhotoFile != null;
            if (resultCode == RESULT_OK)
            {
                mPhotos.add(createImageItem(mCurrentPhotoFile.getAbsolutePath()));
                mAdapter.notifyDataSetChanged();
                mImageToSend = mCurrentPhotoFile.getAbsolutePath();
                discoverService();
            } else
            {
                mCurrentPhotoFile.delete();
            }
            mCurrentPhotoFile = null;
        }
    }

    private void showPicture(ImageItem item)
    {
        Intent intent = new Intent();
        intent.setAction(Intent.ACTION_VIEW);
        intent.setDataAndType(Uri.parse("file:"+item.getPath()), "image/*");
        startActivity(intent);
    }

    private void takePicture()
    {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            // Create the File where the photo should go
            File photoFile = null;
            try {
                photoFile = createImageFile();
            } catch (IOException ex) {
                // Error occurred while creating the File
                Toast.makeText(this, "Failed to create camera file", Toast.LENGTH_LONG).show();
            }
            // Continue only if the File was successfully created
            if (photoFile != null) {
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT,
                        Uri.fromFile(photoFile));
                startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE);
            }
        }
    }

    private File createImageFile() throws IOException
    {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        if (!fImageDir.exists())
        {
            if (!fImageDir.mkdirs())
                return null;
        }
        File image = File.createTempFile(
                imageFileName,  /* prefix */
                ".jpg",         /* suffix */
                fImageDir      /* directory */
        );

        // Save a file: path for use with ACTION_VIEW intents
        mCurrentPhotoFile = image;
        //mCurrentPhotoPath = "file:" + image.getAbsolutePath();
        return image;
    }

    private ImageItem createImageItem(String path)
    {
        final int px = mGridView.getColumnWidth();;
        Bitmap thumbnail = ThumbnailUtils.extractThumbnail(BitmapFactory.decodeFile(path), px, px);
        return new ImageItem(thumbnail, path);
    }

    private void DeleteAllFiles(File folder)
    {
        if (!folder.exists())
            return;
        for (File f : folder.listFiles())
            f.delete();
    }

    static final int PORT=7272;
    private WifiP2pManager mManager;
    private WifiP2pManager.Channel mChannel;
    private IntentFilter mWifiIntentFilter;
    private WifiReceiver mWifiReceiver = new WifiReceiver();
    private WifiP2pDnsSdServiceRequest serviceRequest;
    final HashMap<String, String> mBuddies = new HashMap<>();

    private void addLocalService()
    {
        Map record = new HashMap<String, String>();
        record.put("listenport", String.valueOf(PORT));
        record.put("buddyname", "MyRevolution" + (int)(Math.random() *1000));
        record.put("available", "visible");

        WifiP2pDnsSdServiceInfo serviceInfo = WifiP2pDnsSdServiceInfo.newInstance("_images", "_myrevolution._tcp", record);
        mManager.addLocalService(mChannel, serviceInfo, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                ///
            }

            @Override
            public void onFailure(int reason) {
                throw new RuntimeException("Fuck you");
            }
        });
    }

    private void initWifi()
    {
        mWifiIntentFilter = new IntentFilter();
        //  Indicates a change in the Wi-Fi P2P status.
        mWifiIntentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);

        // Indicates a change in the list of available peers.
        mWifiIntentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);

        // Indicates the state of Wi-Fi P2P connectivity has changed.
        mWifiIntentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);

        // Indicates this device's details have changed.
        mWifiIntentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);

        mManager = (WifiP2pManager)getSystemService(Context.WIFI_P2P_SERVICE);
        mChannel = mManager.initialize(this, getMainLooper(), null);

        addLocalService();
        //discoverService();
    }

    private void discoverService()
    {
        WifiP2pManager.DnsSdTxtRecordListener txtListener = new WifiP2pManager.DnsSdTxtRecordListener() {
            @Override
        /* Callback includes:
         * fullDomain: full domain name: e.g "printer._ipp._tcp.local."
         * record: TXT record dta as a map of key/value pairs.
         * device: The device running the advertised service.
         */

            public void onDnsSdTxtRecordAvailable(
                    String fullDomain, Map record, WifiP2pDevice device) {
                mBuddies.put(device.deviceAddress, (String)record.get("buddyname"));
            }
        };

        WifiP2pManager.DnsSdServiceResponseListener servListener = new WifiP2pManager.DnsSdServiceResponseListener() {
            @Override
            public void onDnsSdServiceAvailable(String instanceName, String registrationType,
                                                WifiP2pDevice resourceType) {

                // Update the device name with the human-friendly version from
                // the DnsTxtRecord, assuming one arrived.
                resourceType.deviceName = mBuddies
                        .containsKey(resourceType.deviceAddress) ? mBuddies
                        .get(resourceType.deviceAddress) : resourceType.deviceName;

                /*
                // Add to the custom adapter defined specifically for showing
                // wifi devices.
                WiFiDirectServicesList fragment = (WiFiDirectServicesList) getFragmentManager()
                        .findFragmentById(R.id.frag_peerlist);
                WiFiDevicesAdapter adapter = ((WiFiDevicesAdapter) fragment
                        .getListAdapter());

                adapter.add(resourceType);
                adapter.notifyDataSetChanged();
                Log.d(TAG, "onBonjourServiceAvailable " + instanceName);*/

                SendImage(resourceType);

                throw  new RuntimeException("Oh god damn");
            }
        };

        mManager.setDnsSdResponseListeners(mChannel, servListener, txtListener);

        serviceRequest = WifiP2pDnsSdServiceRequest.newInstance();
        mManager.addServiceRequest(mChannel,
                serviceRequest,
                new WifiP2pManager.ActionListener() {
                    @Override
                    public void onSuccess() {
                        // Success!
                    }

                    @Override
                    public void onFailure(int code) {
                        throw new RuntimeException("Oh god damn");
                    }
                });

        mManager.discoverServices(mChannel, new WifiP2pManager.ActionListener() {

                    @Override
                    public void onSuccess() {
                        // Success!
                    }

                    @Override
                    public void onFailure(int code) {
                        throw new RuntimeException("Oh god damn");
                    }
                }

        );
    }

    private ImageSender mSender;

    private void SendImage(WifiP2pDevice device)
    {
        WifiP2pConfig config = new WifiP2pConfig();
        config.deviceAddress = device.deviceAddress;
        config.wps.setup = WpsInfo.PBC;

        mManager.connect(mChannel, config, new WifiP2pManager.ActionListener() {

            @Override
            public void onSuccess() {
                // WiFiDirectBroadcastReceiver will notify us. Ignore for now.
            }

            @Override
            public void onFailure(int reason) {
                throw new RuntimeException("Oh god damn");
            }
        });
    }
}

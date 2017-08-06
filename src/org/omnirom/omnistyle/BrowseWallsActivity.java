/*
 *  Copyright (C) 2017 The OmniROM Project
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */
package org.omnirom.omnistyle;

import android.Manifest;
import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.WallpaperManager;
import android.content.DialogInterface;
import android.content.Context;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Point;
import android.graphics.drawable.Drawable;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.View;
import android.view.MenuItem;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import javax.net.ssl.HttpsURLConnection;

import org.json.JSONArray;
import org.json.JSONObject;

import com.squareup.picasso.Picasso;

public class BrowseWallsActivity extends Activity {
    private static final String TAG = "BrowseWallsActivity";
    private static final String IMAGE_TYPE = "image/*";
    private static final int IMAGE_CROP_AND_SET = 1;
    private static final String WALLPAPER_LIST_URI = "https://dl.omnirom.org/images/wallpapers/thumbs/json_wallpapers_xml.php";
    private static final String WALLPAPER_THUMB_URI = "https://dl.omnirom.org/images/wallpapers/thumbs/";
    private static final String WALLPAPER_FULL_URI = "https://dl.omnirom.org/images/wallpapers/";

    private static final boolean DEBUG = false;
    private List<WallpaperInfo> mWallpaperList;
    private List<RemoteWallpaperInfo> mWallpaperUrlList;
    private Resources mRes;
    private GridView mWallpaperView;
    private String mPackageName;
    private WallpaperListAdapter mAdapter;
    private WallpaperRemoteListAdapter mAdapterRemote;
    private int mWallpaperPreviewSize;
    private Runnable mDoAfter;
    private Spinner mLocationSelect;
    private int mCurrentLocation;
    private TextView mNoNetworkMessage;
    private ProgressBar mProgressBar;
    private String mWallpaperDisplayDefault;

    private static final int HTTP_READ_TIMEOUT = 30000;
    private static final int HTTP_CONNECTION_TIMEOUT = 30000;
    private static final int PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE = 0;

    private class WallpaperInfo {
        public String mImage;
        public String mCreator;
        public String mDisplayName;
    }

    private class RemoteWallpaperInfo {
        public String mImage;
        public String mUri;
        public String mThumbUri;
        public String mCreator;
        public String mDisplayName;
    }

    public class WallpaperListAdapter extends ArrayAdapter<WallpaperInfo> {
        private final LayoutInflater mInflater;

        public WallpaperListAdapter(Context context) {
            super(context, R.layout.wallpaper_image, mWallpaperList);
            mInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            WallpaperImageHolder holder = WallpaperImageHolder.createOrRecycle(mInflater, convertView);
            convertView = holder.rootView;
            try {
                WallpaperInfo wi = mWallpaperList.get(position);
                int resId = mRes.getIdentifier(wi.mImage, "drawable", mPackageName);

                if (resId != 0) {
                    Picasso.with(BrowseWallsActivity.this)
                        .load(resId)
                        .resize(mWallpaperPreviewSize, mWallpaperPreviewSize)
                        .centerCrop()
                        .into(holder.mWallpaperImage);
                } else {
                    holder.mWallpaperImage.setImageDrawable(null);
                }

                holder.mWallpaperName.setText(TextUtils.isEmpty(wi.mDisplayName) ? mWallpaperDisplayDefault + " " + (position + 1) : wi.mDisplayName);
                holder.mWallpaperCreator.setVisibility(TextUtils.isEmpty(wi.mCreator) ? View.GONE : View.VISIBLE);
                holder.mWallpaperCreator.setText(wi.mCreator);
            } catch (Exception e) {
                holder.mWallpaperImage.setImageDrawable(null);
            }
            return convertView;
        }
    }

    public class WallpaperRemoteListAdapter extends ArrayAdapter<RemoteWallpaperInfo> {
        private final LayoutInflater mInflater;

        public WallpaperRemoteListAdapter(Context context) {
            super(context, R.layout.wallpaper_image, mWallpaperUrlList);
            mInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            WallpaperImageHolder holder = WallpaperImageHolder.createOrRecycle(mInflater, convertView);
            convertView = holder.rootView;
            try {
                RemoteWallpaperInfo wi = mWallpaperUrlList.get(position);

                Picasso.with(BrowseWallsActivity.this).load(wi.mThumbUri).into(holder.mWallpaperImage);

                holder.mWallpaperName.setText(TextUtils.isEmpty(wi.mDisplayName) ? mWallpaperDisplayDefault + " " + (position + 1) : wi.mDisplayName);
                holder.mWallpaperCreator.setVisibility(TextUtils.isEmpty(wi.mCreator) ? View.GONE : View.VISIBLE);
                holder.mWallpaperCreator.setText(wi.mCreator);
            } catch (Exception e) {
                holder.mWallpaperImage.setImageDrawable(null);
            }
            return convertView;
        }
    }

    public static class WallpaperImageHolder {
        public View rootView;
        public ImageView mWallpaperImage;
        public TextView mWallpaperName;
        public TextView mWallpaperCreator;

        public static WallpaperImageHolder createOrRecycle(LayoutInflater inflater, View convertView) {
            if (convertView == null) {
                convertView = inflater.inflate(R.layout.wallpaper_image, null);
                WallpaperImageHolder holder = new WallpaperImageHolder();
                holder.rootView = convertView;
                holder.mWallpaperImage = (ImageView) convertView.findViewById(R.id.wallpaper_image);
                holder.mWallpaperName = (TextView) convertView.findViewById(R.id.wallpaper_name);
                holder.mWallpaperCreator = (TextView) convertView.findViewById(R.id.wallpaper_creator);
                convertView.setTag(holder);
                return holder;
            } else {
                // Get the ViewHolder back to get fast access to the TextView
                // and the ImageView.
                return (WallpaperImageHolder) convertView.getTag();
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.content_wallpapers);
        mWallpaperDisplayDefault = getResources().getString(R.string.wallpaper_default_name);
        mPackageName = getClass().getPackage().getName();
        getActionBar().setDisplayOptions(ActionBar.DISPLAY_HOME_AS_UP | ActionBar.DISPLAY_SHOW_TITLE);

        mWallpaperList = new ArrayList<WallpaperInfo>();
        mWallpaperUrlList = new ArrayList<RemoteWallpaperInfo>();
        mWallpaperPreviewSize = 300; // in pixel
        mLocationSelect = (Spinner) findViewById(R.id.location_select);
        mNoNetworkMessage = (TextView) findViewById(R.id.no_network_message);
        mProgressBar = (ProgressBar) findViewById(R.id.browse_progress);

        String[] locationList = getResources().getStringArray(R.array.wallpaper_location_list);
        ArrayAdapter<CharSequence> adapter = new ArrayAdapter(this,
                R.layout.spinner_item, locationList);
        mLocationSelect.setAdapter(adapter);

        mLocationSelect.setOnItemSelectedListener(new Spinner.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                mCurrentLocation = position;
                if (position == 0) {
                    mWallpaperView.setAdapter(mAdapter);
                    mNoNetworkMessage.setVisibility(View.GONE);
                    mProgressBar.setVisibility(View.GONE);
                } else {
                    if (isNetworkAvailable()) {
                        mNoNetworkMessage.setVisibility(View.GONE);
                        mWallpaperView.setAdapter(mAdapterRemote);
                        mProgressBar.setVisibility(View.VISIBLE);
                        FetchWallpaperListTask fetch = new FetchWallpaperListTask();
                        fetch.execute();
                    } else {
                        mNoNetworkMessage.setVisibility(View.VISIBLE);
                    }
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> arg0) {
            }
        });
        PackageManager packageManager = getPackageManager();
        try {
            mRes = packageManager.getResourcesForApplication(mPackageName);
            getAvailableWallpapers();

            mWallpaperView = (GridView) findViewById(R.id.wallpaper_images);
            mWallpaperView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> adapterView, View view, final int i, long l) {
                    if (!checkCropActivity()) {
                        AlertDialog.Builder noCropActivityDialog = new AlertDialog.Builder(BrowseWallsActivity.this);
                        noCropActivityDialog.setMessage(getResources().getString(R.string.no_crop_activity_dialog_text));
                        noCropActivityDialog.setTitle(getResources().getString(R.string.no_crop_activity_dialog_title));
                        noCropActivityDialog.setCancelable(false);
                        noCropActivityDialog.setPositiveButton(android.R.string.ok, null);
                        AlertDialog d = noCropActivityDialog.create();
                        d.show();
                        return;
                    }
                    if (mCurrentLocation == 0) {
                        doSetLocalWallpaper(i);
                    } else {
                        doSetRemoteWallpaper(i);
                    }
                }
            });
            mAdapter = new WallpaperListAdapter(this);
            mAdapterRemote = new WallpaperRemoteListAdapter(this);

            mWallpaperView.setAdapter(mAdapter);
            mAdapter.notifyDataSetChanged();
        } catch (Exception e) {
            Log.e(TAG, "init failed", e);
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == IMAGE_CROP_AND_SET && resultCode == Activity.RESULT_OK) {
            finish();
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
        }
        return true;
    }

    private void getAvailableWallpapers() throws XmlPullParserException, IOException {
        mWallpaperList.clear();
        InputStream in = null;
        XmlPullParser parser = null;

        try {
            in = mRes.getAssets().open("wallpapers.xml");
            XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
            parser = factory.newPullParser();
            parser.setInput(in, "UTF-8");
            loadResourcesFromXmlParser(parser);
        } finally {
            // Cleanup resources
            if (parser instanceof XmlResourceParser) {
                ((XmlResourceParser) parser).close();
            }
            if (in != null) {
                try {
                    in.close();
                } catch (IOException e) {
                }
            }
        }
    }

    private void loadResourcesFromXmlParser(XmlPullParser parser) throws XmlPullParserException, IOException {
        int eventType = parser.getEventType();
        do {
            if (eventType != XmlPullParser.START_TAG) {
                continue;
            }
            String name = parser.getName();
            if (name.equalsIgnoreCase("wallpaper")) {
                String image = parser.getAttributeValue(null, "image");
                if (image != null) {
                    String creator = parser.getAttributeValue(null, "creator");
                    String displayName = parser.getAttributeValue(null, "name");
                    WallpaperInfo wi = new WallpaperInfo();
                    wi.mImage = image;
                    wi.mCreator = creator;
                    wi.mDisplayName = displayName;
                    if (DEBUG)
                        Log.i(TAG, "add wallpaper " + image + " " + mRes.getIdentifier(image, "drawable", mPackageName));
                    mWallpaperList.add(wi);
                }
            }
        } while ((eventType = parser.next()) != XmlPullParser.END_DOCUMENT);
        if (DEBUG) Log.i(TAG, "loaded size = " + mWallpaperList.size());
    }

    private Intent getCropActivity() {
        final Intent cropAndSetWallpaperIntent = new Intent();
        cropAndSetWallpaperIntent.setComponent(new ComponentName("com.android.gallery3d",
                "com.android.gallery3d.filtershow.crop.CropActivity"));
        return cropAndSetWallpaperIntent;
    }

    private boolean checkCropActivity() {
        final Intent cropAndSetWallpaperIntent = getCropActivity();
        return cropAndSetWallpaperIntent.resolveActivityInfo(getPackageManager(), 0) != null;
    }

    private void doCallCropActivity(Uri imageUri, Point dispSize, int wpWidth, int wpHeight) {
        float spotlightX = (float) dispSize.x / wpWidth;
        float spotlightY = (float) dispSize.y / wpHeight;

        final Intent cropAndSetWallpaperIntent = getCropActivity()
                .setDataAndType(imageUri, IMAGE_TYPE)
                .putExtra(CropExtras.KEY_OUTPUT_X, wpWidth)
                .putExtra(CropExtras.KEY_OUTPUT_Y, wpHeight)
                .putExtra(CropExtras.KEY_ASPECT_X, wpWidth)
                .putExtra(CropExtras.KEY_ASPECT_Y, wpHeight)
                .putExtra(CropExtras.KEY_SPOTLIGHT_X, spotlightX)
                .putExtra(CropExtras.KEY_SPOTLIGHT_Y, spotlightY)
                .putExtra(CropExtras.KEY_SCALE, true)
                .putExtra(CropExtras.KEY_SCALE_UP_IF_NEEDED, true);

        AlertDialog.Builder wallpaperTypeDialog = new AlertDialog.Builder(BrowseWallsActivity.this);
        wallpaperTypeDialog.setTitle(getResources().getString(R.string.wallpaper_type_dialog_title));
        wallpaperTypeDialog.setItems(R.array.wallpaper_type_list, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int item) {
                int wallpaperType = CropExtras.DEFAULT_WALLPAPER_TYPE;
                if (item == 1) {
                    wallpaperType = WallpaperManager.FLAG_SYSTEM;
                } else if (item == 2) {
                    wallpaperType = WallpaperManager.FLAG_LOCK;
                }
                cropAndSetWallpaperIntent.putExtra(CropExtras.KEY_SET_AS_WALLPAPER, true)
                        .putExtra(CropExtras.KEY_WALLPAPER_TYPE, wallpaperType);
                startActivityForResult(cropAndSetWallpaperIntent, IMAGE_CROP_AND_SET);
            }
        });
        AlertDialog d = wallpaperTypeDialog.create();
        d.show();
    }

    private HttpsURLConnection setupHttpsRequest(String urlStr) {
        URL url;
        HttpsURLConnection urlConnection = null;
        try {
            url = new URL(urlStr);
            urlConnection = (HttpsURLConnection) url.openConnection();
            urlConnection.setConnectTimeout(HTTP_CONNECTION_TIMEOUT);
            urlConnection.setReadTimeout(HTTP_READ_TIMEOUT);
            urlConnection.setRequestMethod("GET");
            urlConnection.setDoInput(true);
            urlConnection.connect();
            int code = urlConnection.getResponseCode();
            if (code != HttpsURLConnection.HTTP_OK) {
                Log.d(TAG, "response:" + code);
                return null;
            }
            return urlConnection;
        } catch (Exception e) {
            Log.e(TAG, "Failed to connect to server", e);
            return null;
        }
    }

    private String downloadUrlMemoryAsString(String url) {
        if (DEBUG) Log.d(TAG, "download: " + url);

        HttpsURLConnection urlConnection = null;
        try {
            urlConnection = setupHttpsRequest(url);
            if (urlConnection == null) {
                return null;
            }

            InputStream is = urlConnection.getInputStream();
            ByteArrayOutputStream byteArray = new ByteArrayOutputStream();
            int byteInt;

            while ((byteInt = is.read()) >= 0) {
                byteArray.write(byteInt);
            }

            byte[] bytes = byteArray.toByteArray();
            if (bytes == null) {
                return null;
            }
            String responseBody = new String(bytes, StandardCharsets.UTF_8);

            return responseBody;
        } catch (Exception e) {
            // Download failed for any number of reasons, timeouts, connection
            // drops, etc. Just log it in debugging mode.
            Log.e(TAG, "", e);
            return null;
        } finally {
            if (urlConnection != null) {
                urlConnection.disconnect();
            }
        }
    }

    private List<RemoteWallpaperInfo> getWallpaperList() {
        String wallData = downloadUrlMemoryAsString(WALLPAPER_LIST_URI);
        if (TextUtils.isEmpty(wallData)) {
            return null;
        }
        List<RemoteWallpaperInfo> urlList = new ArrayList<RemoteWallpaperInfo>();
        try {
            JSONArray walls = new JSONArray(wallData);
            for (int i = 0; i < walls.length(); i++) {
                JSONObject build = walls.getJSONObject(i);
                String fileName = build.getString("filename");
                String creator = null;
                if (build.has("creator")) {
                    creator = build.getString("creator");
                }
                String displayName = null;
                if (build.has("name")) {
                    displayName = build.getString("name");
                }
                if (fileName.lastIndexOf(".") != -1) {
                    String ext = fileName.substring(fileName.lastIndexOf("."));
                    if (ext.equals(".png") || ext.equals(".jpg")) {
                        RemoteWallpaperInfo wi = new RemoteWallpaperInfo();
                        wi.mImage = fileName;
                        wi.mThumbUri = WALLPAPER_THUMB_URI + fileName;
                        wi.mUri = WALLPAPER_FULL_URI + fileName;
                        wi.mCreator = creator;
                        wi.mDisplayName = displayName;
                        urlList.add(wi);
                        if (DEBUG) Log.d(TAG, "add remote wallpaper = " + wi.mUri);
                    }
                }
            }
        } catch (Exception e) {
        }
        return urlList;
    }

    private boolean downloadUrlFile(String url, File f) {
        if (DEBUG) Log.d(TAG, "download:" + url);

        HttpsURLConnection urlConnection = null;

        if (f.exists())
            f.delete();

        try {
            urlConnection = setupHttpsRequest(url);
            if (urlConnection == null) {
                return false;
            }
            long len = urlConnection.getContentLength();
            if ((len > 0) && (len < 4L * 1024L * 1024L * 1024L)) {
                byte[] buffer = new byte[262144];

                InputStream is = urlConnection.getInputStream();
                FileOutputStream os = new FileOutputStream(f, false);
                try {
                    int r;
                    while ((r = is.read(buffer)) > 0) {
                        os.write(buffer, 0, r);
                    }
                } finally {
                    os.close();
                }

                return true;
            }
            return false;
        } catch (Exception e) {
            // Download failed for any number of reasons, timeouts, connection
            // drops, etc. Just log it in debugging mode.
            Log.e(TAG, "", e);
            return false;
        } finally {
            if (urlConnection != null) {
                urlConnection.disconnect();
            }
        }
    }

    private class FetchWallpaperListTask extends AsyncTask<Void, Void, Void> {
        private boolean mError;

        protected Void doInBackground(Void... params) {
            mWallpaperUrlList.clear();
            mError = false;
            List<RemoteWallpaperInfo> wallpaperList = getWallpaperList();
            if (wallpaperList != null) {
                mWallpaperUrlList.addAll(wallpaperList);
            } else {
                mError = true;
            }
            return null;
        }

        protected void onPostExecute(Void feed) {
            mProgressBar.setVisibility(View.GONE);
            if (mError) {
                Toast.makeText(BrowseWallsActivity.this, R.string.download_wallpaper_list_failed_notice, Toast.LENGTH_LONG).show();
            }
            mAdapterRemote.notifyDataSetChanged();
        }
    }

    private class FetchWallpaperTask extends AsyncTask<String, Void, Void> {
        private String mWallpaperFile;

        protected Void doInBackground(String... params) {
            String uri = params[0];
            mWallpaperFile = params[1];
            downloadUrlFile(uri, new File(mWallpaperFile));
            return null;
        }

        protected void onPostExecute(Void feed) {
            mProgressBar.setVisibility(View.GONE);
            doSetRemoteWallpaperPost(mWallpaperFile);
        }
    }

    private void runWithStoragePermissions(Runnable doAfter) {
        if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            mDoAfter = doAfter;
            requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                    PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE);
        } else {
            doAfter.run();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    if (mDoAfter != null) {
                        mDoAfter.run();
                        mDoAfter = null;
                    }
                }
            }
        }
    }

    private void doSetRemoteWallpaper(final int position) {
        runWithStoragePermissions(new Runnable() {
            @Override
            public void run() {
                // no need to save for later - just always overwrite
                String fileName = "tmp_wallpaper";
                RemoteWallpaperInfo ri = mWallpaperUrlList.get(position);
                File localWallpaperFile = new File(getExternalCacheDir(), fileName);
                mProgressBar.setVisibility(View.VISIBLE);
                Toast.makeText(BrowseWallsActivity.this, R.string.download_wallpaper_notice, Toast.LENGTH_SHORT).show();
                FetchWallpaperTask fetch = new FetchWallpaperTask();
                fetch.execute(ri.mUri, localWallpaperFile.getAbsolutePath());
            }
        });
    }

    private void doSetRemoteWallpaperPost(String wallpaperFile) {
        if (!new File(wallpaperFile).exists()) {
            Toast.makeText(BrowseWallsActivity.this, R.string.download_wallpaper_failed_notice, Toast.LENGTH_LONG).show();
            return;
        }
        WallpaperManager wpm = WallpaperManager.getInstance(getApplicationContext());
        final int wpWidth = wpm.getDesiredMinimumWidth();
        final int wpHeight = wpm.getDesiredMinimumHeight();
        Display disp = getWindowManager().getDefaultDisplay();
        final Point dispSize = new Point();
        disp.getRealSize(dispSize);

        Bitmap image = BitmapFactory.decodeFile(wallpaperFile);
        final Uri uri = Uri.fromFile(new File(wallpaperFile));
        if (DEBUG) Log.d(TAG, "crop uri = " + uri);

        // if that image ratio is close to the display size ratio
        // assume this wall is meant to be fullscreen without scrolling
        float displayRatio = (float) Math.round(((float) dispSize.x / dispSize.y) * 10) / 10;
        float imageRatio = (float) Math.round(((float) image.getWidth() / image.getHeight()) * 10) / 10;
        if (displayRatio != imageRatio) {
            // ask if scrolling wallpaper should be used original size
            // or if it should be cropped to image size
            AlertDialog.Builder scrollingWallDialog = new AlertDialog.Builder(BrowseWallsActivity.this);
            scrollingWallDialog.setMessage(getResources().getString(R.string.scrolling_wall_dialog_text));
            scrollingWallDialog.setTitle(getResources().getString(R.string.scrolling_wall_dialog_title));
            scrollingWallDialog.setCancelable(false);
            scrollingWallDialog.setPositiveButton(R.string.scrolling_wall_yes, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    doCallCropActivity(uri, dispSize, wpWidth, wpHeight);
                }
            });
            scrollingWallDialog.setNegativeButton(R.string.scrolling_wall_no, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    doCallCropActivity(uri, dispSize, dispSize.x, dispSize.y);
                }
            });
            AlertDialog d = scrollingWallDialog.create();
            d.show();
        } else {
            doCallCropActivity(uri, dispSize, dispSize.x, dispSize.y);
        }
    }

    private void doSetLocalWallpaper(final int position) {
        WallpaperManager wpm = WallpaperManager.getInstance(getApplicationContext());
        final int wpWidth = wpm.getDesiredMinimumWidth();
        final int wpHeight = wpm.getDesiredMinimumHeight();
        Display disp = getWindowManager().getDefaultDisplay();
        final Point dispSize = new Point();
        disp.getRealSize(dispSize);

        WallpaperInfo wi = mWallpaperList.get(position);
        final int resId = mRes.getIdentifier(wi.mImage, "drawable", mPackageName);
        if (resId == 0) {
            Log.e(TAG, "Wallpaper resource undefined for position = " + position);
            return;
        }
        final Uri uri = Uri.parse("android.resource://" + mPackageName + "/" + resId);
        Drawable image = mRes.getDrawable(resId, null);
        if (DEBUG) Log.d(TAG, "crop uri = " + uri);

        // if that image ratio is close to the display size ratio
        // assume this wall is meant to be fullscreen without scrolling
        float displayRatio = (float) Math.round(((float) dispSize.x / dispSize.y) * 10) / 10;
        float imageRatio = (float) Math.round(((float) image.getIntrinsicWidth() / image.getIntrinsicHeight()) * 10) / 10;
        if (displayRatio != imageRatio) {
            // ask if scrolling wallpaper should be used original size
            // or if it should be cropped to image size
            AlertDialog.Builder scrollingWallDialog = new AlertDialog.Builder(BrowseWallsActivity.this);
            scrollingWallDialog.setMessage(getResources().getString(R.string.scrolling_wall_dialog_text));
            scrollingWallDialog.setTitle(getResources().getString(R.string.scrolling_wall_dialog_title));
            scrollingWallDialog.setCancelable(false);
            scrollingWallDialog.setPositiveButton(R.string.scrolling_wall_yes, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    doCallCropActivity(uri, dispSize, wpWidth, wpHeight);
                }
            });
            scrollingWallDialog.setNegativeButton(R.string.scrolling_wall_no, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    doCallCropActivity(uri, dispSize, dispSize.x, dispSize.y);
                }
            });
            AlertDialog d = scrollingWallDialog.create();
            d.show();
        } else {
            doCallCropActivity(uri, dispSize, dispSize.x, dispSize.y);
        }
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetwork = connectivityManager.getActiveNetworkInfo();
        if (activeNetwork != null && activeNetwork.isConnected()) {
            return true;
        } else {
            return false;
        }
    }
}


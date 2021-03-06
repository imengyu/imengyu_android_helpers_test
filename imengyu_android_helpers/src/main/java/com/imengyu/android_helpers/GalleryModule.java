package com.imengyu.android_helpers;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.media.MediaMetadataRetriever;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.provider.MediaStore;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.huantansheng.easyphotos.EasyPhotos;
import com.huantansheng.easyphotos.models.album.AlbumModel;
import com.huantansheng.easyphotos.models.album.entity.AlbumItem;
import com.huantansheng.easyphotos.models.album.entity.Photo;
import com.imengyu.android_helpers.adapter.VideoListListAdapter;
import com.imengyu.android_helpers.model.VideoListItem;
import com.imengyu.android_helpers.model.VideoModel;
import com.imengyu.android_helpers.model.entity.Video;
import com.imengyu.android_helpers.utils.BitmapUtils;
import com.imengyu.android_helpers.utils.CacheUtils;
import com.imengyu.android_helpers.utils.GlideEngine;
import com.imengyu.android_helpers.utils.MD5Utils;
import com.imengyu.android_helpers.utils.StringUtils;
import com.imengyu.android_helpers.widget.RecyclerViewEmptySupport;
import com.imengyu.android_helpers.wxvideoeditt.EsayVideoEditActivity;
import com.kongzue.dialogx.dialogs.BottomMenu;
import com.kongzue.dialogx.dialogs.FullScreenDialog;
import com.kongzue.dialogx.dialogs.WaitDialog;
import com.kongzue.dialogx.interfaces.OnBindView;
import com.taobao.weex.bridge.JSCallback;
import com.taobao.weex.common.WXModule;
import com.yalantis.ucrop.UCrop;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import io.dcloud.feature.uniapp.annotation.UniJSMethod;
import io.dcloud.feature.uniapp.utils.UniLogUtils;

@Keep
public class GalleryModule extends WXModule {
    String TAG = "GalleryModule";

    /**
     * ????????????????????????????????????
     * @param options {}
     * @param callback  ??????
     * ???????????????
     *                  {
     *                      list: [
     *                          //???????????????
     *                          {
     *                              name: string, //???????????????
     *                              folderPath: string,
     *                              coverImagePath: string, //???????????????????????????
     *                              //?????????????????????
     *                              photos: [
     *                                  name: string,//????????????
     *                                  path: string,//???????????????
     *                                  type: string,//????????????
     *                                  width: number,//????????????
     *                                  height: number,//????????????
     *                                  orientation: number,//??????????????????
     *                                  size: number,//??????????????????????????????Bytes
     *                                  duration: number,//??????????????????????????????
     *                                  time: number
     *                              ]
     *                          }
     *                      ]
     *                  }
     */
    @Keep
    @UniJSMethod
    public void getSystemAlbums(JSONObject options, final JSCallback callback) {
        final Context context = mUniSDKInstance.getContext();
        AlbumModel albumModel = AlbumModel.getInstance();
        albumModel.query(context, () -> {
            JSONObject jsonObject = new JSONObject();
            List<AlbumItem> albums = albumModel.getAlbumItems();
            jsonObject.put("list", albums);
            callback.invoke(jsonObject);
        });
    }

    /**
     * ?????????????????????????????? ????????????
     * @param options {}
     * @param callback  ??????
     * ???????????????
     *                  {
     *                      videos: [
     *                          name: string,//????????????
     *                          path: string,//???????????????
     *                          size: number,//??????????????????????????????Bytes
     *                          duration: number,//??????????????????????????????
     *                      ]
     *                  }
     */
    @Keep
    @UniJSMethod
    public void getSystemVideos(JSONObject options, final JSCallback callback) {
        final Context context = mUniSDKInstance.getContext();
        VideoModel videoModel = VideoModel.getInstance();
        videoModel.query(context, () -> {
            JSONObject jsonObject = new JSONObject();
            List<Video> videos = videoModel.getItems();
            jsonObject.put("videos", videos);
            callback.invoke(jsonObject);
        });
    }

    /**
     * ??????????????????????????? ????????????
     * @param options
     *                  {
     *                      path: string //???????????????
     *                  }
     * @param callback  ??????
     * ???????????????
     *                  {
     *                      videoThumbnail: string //??????????????????????????????
     *                  }
     */
    @Keep
    @UniJSMethod
    public void getVideoThumbnail(JSONObject options, final JSCallback callback) {
        String path = options.getString("path");
        String name = MD5Utils.md5(path);
        String cachePath = BitmapUtils.checkBitmapTempExists(mWXSDKInstance.getContext(), name);
        if(cachePath != null) {
            //?????????cache??????????????????
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("videoThumbnail", cachePath);
            callback.invoke(jsonObject);
        }
        else {
            new Thread(() -> {
                //????????????????????????????????????????????????Uniapp??????
                Bitmap videoThumbnail = ThumbnailUtils.createVideoThumbnail(path, MediaStore.Video.Thumbnails.MINI_KIND);

                JSONObject jsonObject = new JSONObject();
                jsonObject.put("videoThumbnail", BitmapUtils.saveBitmapToTemp(mWXSDKInstance.getContext(), videoThumbnail, name));
                callback.invoke(jsonObject);

            }).start();
        }
    }

    /**
     * ?????????????????????Exif????????????
     * @param path ??????????????????
     * @return ??????????????????
     */
    @Keep
    @UniJSMethod
    public int getImageExifOrientation(String path) {
       return BitmapUtils.getImageExifOrientation(path);
    }

    /**
     * ???????????????????????? ????????????
     * @param options
     *                  {
     *                      path: string //???????????????
     *                  }
     * @param callback  ??????
     * ???????????????
     *                  {
     *                      width: number,
     *                      height: number,
     *                      size: number,//???????????????
     *                      rotation: number,//?????????????????????
     *                  }
     */
    @Keep
    @UniJSMethod
    public void getVideoSize(JSONObject options, final JSCallback callback) {
        String path = options.getString("path");
        new Thread(() -> {

            if(!new File(path).exists()) {
                JSONObject jsonObject = new JSONObject();
                jsonObject.put("errMsg", "Not exists");
                callback.invoke(jsonObject);
                return;
            }

            try {
                MediaMetadataRetriever retriever = new MediaMetadataRetriever();
                retriever.setDataSource(path);
                String width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH); //???
                String height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT); //???
                String rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION);//?????????????????????
                long duration = Long.parseLong(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)) * 1000;//???????????????
                retriever.release();

                JSONObject jsonObject = new JSONObject();
                jsonObject.put("width", width);
                jsonObject.put("height", height);
                jsonObject.put("rotation", rotation);
                jsonObject.put("duration", duration);
                callback.invoke(jsonObject);
            }
            catch (Exception e) {
                JSONObject jsonObject = new JSONObject();
                jsonObject.put("errMsg", e.getLocalizedMessage());
                callback.invoke(jsonObject);
            }

        }).start();
    }

    private final int REQUEST_CODE_VIDEO_EDIT = 10;
    private final int REQUEST_CODE_CHOOSE_IMAGE = 11;

    private JSCallback callbackVideoEditActivity = null;
    private JSCallback callbackChooseImage = null;
    private JSCallback callbackCropImage = null;

    /**
     * ????????????????????????
     * @param options
     *                  {
     *                      path: string, //???????????????
     *                      title: string, //?????????????????????????????????
     *                      maxDuration: number, //??????????????????????????????????????????60???
     *                  }
     * @param callback
     *                  {
     *                      tempVideoPath: string, //??????????????????????????????????????????????????????????????????deleteTempVideo??????
     *                      success: boolean //??????????????????
     *                  }
     */
    @Keep
    @UniJSMethod
    public void startVideoEditActivity(JSONObject options, final JSCallback callback){
        String path = options.getString("path");
        String title = options.getString("title");
        Integer maxDuration = options.getInteger("maxDuration");

        Intent intent = new Intent();
        intent.putExtra(EsayVideoEditActivity.PATH, path);
        intent.putExtra(EsayVideoEditActivity.TITLE, title);
        intent.putExtra(EsayVideoEditActivity.DURATION, maxDuration);
        intent.setClass(mUniSDKInstance.getContext(), EsayVideoEditActivity.class);

        callbackVideoEditActivity = callback;
        ((Activity) mWXSDKInstance.getContext()).startActivityForResult(intent, REQUEST_CODE_VIDEO_EDIT);
    }

    /**
     * ???????????????????????????????????? ????????????
     * @param options
     *                  {
     *                      path: string //?????????????????????
     *                  }
     * @param callback  ??????
     *                  {
     *                      success: boolean, //????????????
     *                      errMsg: string //????????????????????????????????????
     *                  }
     */
    @Keep
    @UniJSMethod
    public void deleteTempVideo(JSONObject options, final JSCallback callback) {
        String path = options.getString("path");

        new Thread(() -> {

            boolean deleteSuccess = false;
            String err = "";
            try {
                File file = new File(path);
                if (file.exists())
                    deleteSuccess = file.delete();
                else
                    err = "File not exists";
            } catch (SecurityException e) {
                err = e.getLocalizedMessage();
                e.printStackTrace();
            }

            JSONObject jsonObject = new JSONObject();
            jsonObject.put("success", deleteSuccess);
            jsonObject.put("errMsg", err);
            callback.invoke(jsonObject);

        }).start();
    }

    /**
     * ???????????????????????? (UCorp)
     * @param options
     * {
     *      sourceFilePath: string, //???????????????
     *      useSourceImageAspectRatio?: boolean, //?????????????????????????????????
     *      aspectRatio?: number[], //????????????????????????????????????useSourceImageAspectRatio???true???????????????
     *      maxResultSize?: number[], //????????????????????????????????????
     *      activeControlsWidgetColor?: string, //????????????????????????
     *      cropFrameColor?: string, //???????????????
     *      cropGridColor?: string, //?????????????????????
     *      dimmedLayerColor?: string, //?????????????????????????????????????????????
     *      logoColor?: string, //?????????????????????????????????????????????????????????
     *      rootViewBackgroundColor?: string, //?????????????????????
     *      statusBarColor?: string, //???????????????
     *      toolbarColor?: string, //???????????????
     *      toolbarWidgetColor?: string, //?????????????????????????????????????????????????????????????????????
     *      toolbarTitle?: string, //???????????????
     *      compressionQuality?: number, //[0 - 100] ???????????????????????????png??????????????????
     *      compressionFormat?: 'jpeg'|'png'|'webp', //????????????????????????
     *      circleDimmedLayer?: boolean, //??????????????????????????????
     *      showCropFrame?: boolean, //???????????????????????????
     *      showCropGrid?: boolean, //?????????????????????????????????
     *      freeStyleCropEnabled?: boolean, //???????????????????????????????????????
     *      hideBottomControls?: boolean, //?????????????????????????????????
     *      maxScaleMultiplier?: integer, //??????????????????????????????????????????????????????????????????????????????
     *      maxBitmapSize?: integer, //?????????????????????Uri?????????????????????????????????????????????????????????????????????
     *      imageToCropBoundsAnimDuration?: integer, //??????????????????????????????????????????????????????????????????
     *      cropFrameStrokeWidth?: integer, //?????????????????????????????????????????????
     *      cropGridRowCount?: integer, //????????????????????????
     *      cropGridColumnCount?: integer, //????????????????????????
     * }
     * @param callback
     * {
     *      success: boolean,
     *      errMsg: string,
     *      tempFilePath: string, //??????????????????????????????
     * }
     */
    @Keep
    @UniJSMethod
    public void startCropImageActivity(JSONObject options, final JSCallback callback) {
        Activity context = (Activity)mWXSDKInstance.getContext();
        callbackCropImage = callback;

        //??????
        if(!options.containsKey("sourceFilePath")) {
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("success", false);
            jsonObject.put("errMsg", "No sourceFilePath");
            callback.invoke(jsonObject);
            return;
        }

        //??????????????????
        File srcFile = new File(options.getString("sourceFilePath"));
        if(!srcFile.exists()) {
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("success", false);
            jsonObject.put("errMsg", "Source file does not exist");
            callback.invoke(jsonObject);
            return;
        }

        UCrop.Options uCropOptions1 = new UCrop.Options();
        if(options.containsKey("activeControlsWidgetColor"))
            uCropOptions1.setActiveControlsWidgetColor(Color.parseColor(options.getString("activeControlsWidgetColor")));
        if(options.containsKey("cropFrameColor"))
            uCropOptions1.setCropFrameColor(Color.parseColor(options.getString("cropFrameColor")));
        if(options.containsKey("cropGridColor"))
            uCropOptions1.setCropGridColor(Color.parseColor(options.getString("cropGridColor")));
        if(options.containsKey("dimmedLayerColor"))
            uCropOptions1.setDimmedLayerColor(Color.parseColor(options.getString("dimmedLayerColor")));
        if(options.containsKey("logoColor"))
            uCropOptions1.setLogoColor(Color.parseColor(options.getString("logoColor")));
        if(options.containsKey("rootViewBackgroundColor"))
            uCropOptions1.setRootViewBackgroundColor(Color.parseColor(options.getString("rootViewBackgroundColor")));
        if(options.containsKey("statusBarColor"))
            uCropOptions1.setStatusBarColor(Color.parseColor(options.getString("statusBarColor")));
        if(options.containsKey("toolbarColor"))
            uCropOptions1.setToolbarColor(Color.parseColor(options.getString("toolbarColor")));
        if(options.containsKey("toolbarWidgetColor"))
            uCropOptions1.setToolbarWidgetColor(Color.parseColor(options.getString("toolbarWidgetColor")));
        if(options.containsKey("circleDimmedLayer"))
            uCropOptions1.setCircleDimmedLayer(options.getBoolean("circleDimmedLayer"));
        if(options.containsKey("showCropFrame"))
            uCropOptions1.setShowCropFrame(options.getBoolean("showCropFrame"));
        if(options.containsKey("showCropGrid"))
            uCropOptions1.setShowCropGrid(options.getBoolean("showCropGrid"));
        if(options.containsKey("hideBottomControls"))
            uCropOptions1.setHideBottomControls(options.getBoolean("hideBottomControls"));
        if(options.containsKey("freeStyleCropEnabled"))
            uCropOptions1.setFreeStyleCropEnabled(options.getBoolean("freeStyleCropEnabled"));
        if(options.containsKey("compressionFormat")) {
            String compressionFormat = options.getString("compressionFormat");
            switch (compressionFormat) {
                case "jpeg": uCropOptions1.setCompressionFormat(Bitmap.CompressFormat.JPEG); break;
                case "png": uCropOptions1.setCompressionFormat(Bitmap.CompressFormat.PNG); break;
                case "webp": uCropOptions1.setCompressionFormat(Bitmap.CompressFormat.WEBP); break;
            }
        }

        if(options.containsKey("cropFrameStrokeWidth"))
            uCropOptions1.setCropFrameStrokeWidth(options.getInteger("cropFrameStrokeWidth"));
        if(options.containsKey("cropGridColumnCount"))
            uCropOptions1.setCropGridColumnCount(options.getInteger("cropGridColumnCount"));
        if(options.containsKey("cropGridRowCount"))
            uCropOptions1.setCropGridRowCount(options.getInteger("cropGridRowCount"));
        if(options.containsKey("imageToCropBoundsAnimDuration"))
            uCropOptions1.setImageToCropBoundsAnimDuration(options.getInteger("imageToCropBoundsAnimDuration"));
        if(options.containsKey("maxBitmapSize"))
            uCropOptions1.setMaxBitmapSize(options.getInteger("maxBitmapSize"));
        if(options.containsKey("maxScaleMultiplier"))
            uCropOptions1.setMaxScaleMultiplier(options.getInteger("maxScaleMultiplier"));
        if(options.containsKey("toolbarTitle"))
            uCropOptions1.setToolbarTitle(options.getString("toolbarTitle"));

        //cache??????
        File cacheFile = CacheUtils.getCachePath(context, "/crop-image-cache/", srcFile.getName());
        UCrop uCrop = UCrop.of(Uri.fromFile(srcFile), Uri.fromFile(cacheFile)).withOptions(uCropOptions1);

        if(options.containsKey("useSourceImageAspectRatio") && options.getBoolean("useSourceImageAspectRatio")) {
            uCrop.useSourceImageAspectRatio();
        } else {
            if(options.containsKey("aspectRatio")) {
                JSONArray array = options.getJSONArray("aspectRatio");
                if(array.size() >= 2)
                    uCrop.withAspectRatio(array.getInteger(0), array.getInteger(1));
            }
        }
        if(options.containsKey("maxResultSize")) {
            JSONArray array = options.getJSONArray("maxResultSize");
            if(array.size() >= 2)
                uCrop.withMaxResultSize(array.getInteger(0), array.getInteger(1));
        }

        uCrop.start(context);
    }

    private int compressQuality = 80;
    private boolean compressImage = false;
    private int compressImageWidth = 0;
    private int compressImageHeight = 0;
    private int chooseImageImageDirectionCorrection = 0;

    /**
     * ????????????
     * @param options
     * {
     *     sourceType: [ 'album', 'camera' ], //??? uni.chooseImage sourceType
     *     count: number, //??????????????????
     *     hasGif: boolean, //???????????????????????????true
     *     hasVideo: boolean, //???????????????????????????false
     *     needSize: boolean, //???????????????????????????????????????false,
     *     compressQuality: number,	//???????????????????????????????????????1-100?????????????????????????????????????????????80???
     *     compress: { //???????????????????????????????????????????????????????????????????????????????????????????????????.
     *         //?????????????????????????????????????????????????????????????????????
     *         //????????????????????????????????????????????????????????????????????????????????????
     *         width: Number, //??????????????????????????????px?????????????????????????????????
     *         height: Number, //??????????????????????????????px?????????????????????????????????
     *     },
     *     imageDirectionCorrection: number, //????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????
     * }
     * @param callback ?????????????????? uni.chooseImage ??????
     * {
     *      success: boolean,
     *      errMsg: string,
     *      isOriginal: boolean,  //?????????????????????
     *      tempFilePaths: string[], //??????????????????????????????
     *      tempFiles: {
     *                 name: string, //?????????
     *                 width: number, //??????????????????needSize???true?????????
     *                 height: number, //??????????????????needSize???true?????????
     *                 type: string, //????????????
     *                 duration: number, //???????????????
     *                 orientation: number, //?????????????????????
     *                 time: number, //?????????????????????
     *                 path: string //??????????????????
     *      }[], //??????????????????
     * }
     */
    @Keep
    @UniJSMethod
    public void chooseImage(JSONObject options, final JSCallback callback) {
        try {
            boolean hasAlbum = false, hasCamera = false;
            int selCount = 9;
            boolean hasGif = true;
            boolean hasVideo = false;
            boolean needSize = false;

            callbackChooseImage = callback;

            final Activity activity = (Activity) mWXSDKInstance.getContext();

            if (options.containsKey("sourceType")) {
                JSONArray sourceType = options.getJSONArray("sourceType");
                for (int i = 0; i < sourceType.size(); i++)
                    if ("album".equals(sourceType.get(i)))
                        hasAlbum = true;
                    else if ("camera".equals(sourceType.get(i)))
                        hasCamera = true;
            } else {
                hasAlbum = true;
                hasCamera = true;
            }
            if (options.containsKey("count"))
                selCount = options.getInteger("count");
            if (options.containsKey("hasGif"))
                hasGif = options.getBoolean("hasGif");
            if (options.containsKey("hasVideo"))
                hasVideo = options.getBoolean("hasVideo");
            if (options.containsKey("needSize"))
                needSize = options.getBoolean("needSize");
            if (options.containsKey("compressQuality"))
                compressQuality = options.getInteger("compressQuality");
            else
                compressQuality = 80;
            if (options.containsKey("imageDirectionCorrection"))
                chooseImageImageDirectionCorrection = options.getInteger("imageDirectionCorrection");
            else
                chooseImageImageDirectionCorrection = 0;
            if (options.containsKey("compress")) {
                compressImage = true;

                JSONObject compress = options.getJSONObject("compress");
                if (compress.containsKey("width"))
                    compressImageWidth = compress.getInteger("width");
                else
                    compressImageWidth = 0;
                if (compress.containsKey("height"))
                    compressImageHeight = compress.getInteger("height");
                else
                    compressImageHeight = 0;
                if (compressImageHeight == compressImageWidth && compressImageWidth == 0)
                    compressImage = false;
            } else
                compressImage = false;


            if (hasAlbum && hasCamera) {
                int finalSelCount = selCount;
                boolean finalHasVideo = hasVideo;
                boolean finalHasGif = hasGif;
                boolean finalNeedSize = needSize;
                BottomMenu.show(new String[]{
                        "????????????????????????", "????????????????????????"
                })
                .setCancelButton("??????")
                .setOnMenuItemClickListener((dialog, text, index) -> {
                    if (index == 0) {
                        EasyPhotos.createCamera(activity, finalNeedSize)
                                .setFileProviderAuthority(mWXSDKInstance.getContext().getPackageName() + ".android_helpers.fileprovider")
                                .start(REQUEST_CODE_CHOOSE_IMAGE);
                    } else {
                        EasyPhotos.createAlbum(activity, false, finalNeedSize, GlideEngine.getInstance())
                                .setFileProviderAuthority(mWXSDKInstance.getContext().getPackageName() + ".android_helpers.fileprovider")
                                .setGif(finalHasGif)
                                .setVideo(finalHasVideo)
                                .setCount(finalSelCount)
                                .start(REQUEST_CODE_CHOOSE_IMAGE);
                    }
                    return false;
                }).setCancelButtonClickListener((baseDialog, v) -> {
                    JSONObject jsonObject = new JSONObject();
                    jsonObject.put("success", false);
                    jsonObject.put("errMsg", "cancel");
                    callback.invoke(jsonObject);
                    return false;
                }).setOnBackPressedListener(() -> {
                    JSONObject jsonObject = new JSONObject();
                    jsonObject.put("success", false);
                    jsonObject.put("errMsg", "cancel");
                    callback.invoke(jsonObject);
                    return false;
                });
            } else if (hasAlbum) {
                EasyPhotos.createAlbum(activity, false, needSize, GlideEngine.getInstance())
                        .setFileProviderAuthority(mWXSDKInstance.getContext().getPackageName() + ".android_helpers.fileprovider")
                        .setCount(selCount)
                        .setGif(hasGif)
                        .setVideo(hasVideo)
                        .start(REQUEST_CODE_CHOOSE_IMAGE);
            } else if (hasCamera) {
                EasyPhotos.createCamera(activity, needSize)
                        .setFileProviderAuthority(mWXSDKInstance.getContext().getPackageName() + ".android_helpers.fileprovider")
                        .start(REQUEST_CODE_CHOOSE_IMAGE);
            }
        } catch (Exception e) {
            e.printStackTrace();
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("success", false);
            jsonObject.put("errMsg", e.getMessage());
            jsonObject.put("stackTrace", e.getStackTrace());
            callbackCropImage.invoke(jsonObject);
        }
    }

    /**
     * ??????????????????
     * @param options
     * {
     *     title: string, //???????????????
     *     showCameraButton: boolean, //????????????????????????, ????????????
     * }
     * @param callback ??????
     * {
     *     chooseVideo: {
     *        duration: number, //??????ms
     *        path: string,  //??????
     *        name: string,  //??????
     *        size: number,  //????????????
     *     },
     *     chooseVideoPath: string,  //??????????????????????????? chooseVideo.path?????????????????????????????????????????? "camera"
     *     success: boolean, //????????????
     *     errMsg: string
     * }
     */
    @Keep
    @UniJSMethod
    public void chooseVideo(JSONObject options, final JSCallback callback) {

        Activity activity = (Activity) mWXSDKInstance.getContext();
        String title = "";
        boolean showCameraButton = true;
        if(options.containsKey("title")) title = options.getString("title");
        if(options.containsKey("showCameraButton")) showCameraButton = options.getBoolean("showCameraButton");

        final WaitDialog tipDialog = WaitDialog.show("?????????????????????");
        final String finalTitle = title;
        boolean finalShowCameraButton = showCameraButton;
        VideoModel videoModel = VideoModel.getInstance();
        videoModel.query(activity, () -> {
            List<Video> videos = videoModel.getItems();

            tipDialog.doDismiss();
            FullScreenDialog.show(new OnBindView<FullScreenDialog>(R.layout.layout_choose_video) {
                @Override
                public void onBind(FullScreenDialog dialog, View rootView) {
                    RecyclerViewEmptySupport recyclerView = rootView.findViewById(R.id.recycler_view);

                    View layout_empty = rootView.findViewById(R.id.layout_empty);
                    recyclerView.setEmptyView(layout_empty);

                    if(!finalTitle.equals("")) {
                        TextView text_title = rootView.findViewById(R.id.text_title);
                        text_title.setText(finalTitle);
                    }

                    rootView.findViewById(R.id.btn_close).setOnClickListener((v) -> {
                        dialog.dismiss();
                        JSONObject jsonObject = new JSONObject();
                        jsonObject.put("success", false);
                        jsonObject.put("errMsg", "cancel");
                        callback.invoke(jsonObject);
                    });

                    //Load list
                    List<VideoListItem> videoList = new ArrayList<>();
                    if(finalShowCameraButton) {
                        VideoListItem item = new VideoListItem();
                        item.durationString = "";
                        item.path = "camera";
                        item.isCameraButton = true;
                        videoList.add(item);
                    }
                    for (Video v : videos) {
                        VideoListItem item = new VideoListItem();
                        item.durationString = StringUtils.getTimeString(v.duration);
                        item.path = v.path;
                        item.videoId = v.videoId;
                        videoList.add(item);
                    }

                    GridLayoutManager gridLayoutManager = new GridLayoutManager(activity, 4);
                    VideoListListAdapter adapter = new VideoListListAdapter(activity, R.layout.item_video_list_item, videoList);
                    adapter.setListOnItemClickListener((parent, view, position, id) -> {
                        dialog.dismiss();

                        if(finalShowCameraButton && position == 0) {
                            //????????????
                            JSONObject jsonObject = new JSONObject();
                            jsonObject.put("chooseVideoPath", "camera");
                            jsonObject.put("success", true);
                            jsonObject.put("errMsg", "ok");
                            callback.invoke(jsonObject);
                            return;
                        }

                        Video video = videos.get(finalShowCameraButton ? (position - 1) : position);
                        //????????????
                        JSONObject jsonObject = new JSONObject();
                        JSONObject jvideo = new JSONObject();
                        jvideo.put("duration", video.duration);
                        jvideo.put("path", video.path);
                        jvideo.put("name", video.name);
                        jvideo.put("size", video.size);
                        jsonObject.put("chooseVideo", jvideo);
                        jsonObject.put("chooseVideoPath", video.path);
                        jsonObject.put("success", true);
                        jsonObject.put("errMsg", "ok");
                        callback.invoke(jsonObject);
                    });

                    recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
                        @Override
                        public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                            super.onScrollStateChanged(recyclerView, newState);
                            if(newState == RecyclerView.SCROLL_STATE_IDLE) {
                                List<Integer> changedList = adapter.getChangedItems();
                                for (Integer i : changedList)
                                    adapter.notifyItemChanged(i);
                                changedList.clear();
                            }
                        }
                    });
                    recyclerView.setAdapter(adapter);
                    recyclerView.setLayoutManager(gridLayoutManager);
                    recyclerView.postDelayed(() -> {
                        List<Integer> changedList = adapter.getChangedItems();
                        for (Integer i : changedList)
                            adapter.notifyItemChanged(i);
                        changedList.clear();
                    }, 1000);
                }
            });
        });
    }

    /**
     * ????????????
     * @param options
     * {
     *     path: string, //???????????????
     *     //???????????????????????????????????????????????????????????????????????????
     *     //????????????????????????????????????????????????????????????????????????????????????
     *     width?: number, //????????????.
     *     height?: number, //????????????
     *     quality?: number, //?????????????????????80
     *     directionCorrection?: number, //?????????????????????????????????0??
     * }
     * @param callback
     * {
     *     path: string,  //??????????????????????????????
     *     success: boolean, //????????????
     *     errMsg: string
     * }
     */
    @Keep
    @UniJSMethod
    public void compressImage(JSONObject options, final JSCallback callback) {

        String path;

        if(options.containsKey("path")) path = options.getString("path");
        else {
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("success", false);
            jsonObject.put("errMsg", "path must provide");
            callback.invoke(jsonObject);
            return;
        }

        int width = 0;
        int height = 0;
        int quality = 80;
        int directionCorrection = 0;

        if(options.containsKey("width")) width = options.getInteger("width");
        if(options.containsKey("height")) height = options.getInteger("height");
        if(options.containsKey("quality")) quality = options.getInteger("quality");
        if(options.containsKey("directionCorrection")) directionCorrection = options.getInteger("directionCorrection");

        String name = MD5Utils.md5(path);
        Bitmap b = BitmapFactory.decodeFile(path);
        File cacheFile = CacheUtils.getCachePath(mWXSDKInstance.getContext(), "/compress-image-cache/", name);
        if (b != null && cacheFile != null) {
            try {
                int orientation = BitmapUtils.getImageExifOrientation(path);

                if(width == 0 && height == 0) {
                    width = b.getWidth();
                    height = b.getHeight();
                }
                if(width > 0 || height > 0) {
                    BitmapUtils.imageFixCompress(b,
                            quality,
                            width,
                            height,
                            orientation + directionCorrection,
                            cacheFile);
                    path = cacheFile.getAbsolutePath();
                }


                JSONObject jsonObject = new JSONObject();
                jsonObject.put("success", true);
                jsonObject.put("path", path);
                jsonObject.put("errMsg", "ok");
                callback.invoke(jsonObject);
            } catch (Exception e) {
                e.printStackTrace();
                UniLogUtils.w("Failed to compress image \"" + path + "\"");

                JSONObject jsonObject = new JSONObject();
                jsonObject.put("success", false);
                jsonObject.put("path", path);
                jsonObject.put("errMsg", "Failed to compress image \"" + path + "\": " + e.getLocalizedMessage());
                callback.invoke(jsonObject);
            }
        }
        if (b != null)
            b.recycle();
    }


    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        Context context = mWXSDKInstance.getContext();
        if (requestCode == REQUEST_CODE_VIDEO_EDIT && callbackVideoEditActivity != null) {
            if (resultCode == Activity.RESULT_OK) {
                //VideoEditActivity ????????????
                JSONObject result = new JSONObject();
                result.put("success", data.getExtras().getBoolean("success", false));
                result.put("cancel", false);
                result.put("tempVideoPath", data.getExtras().getString("path"));
                callbackVideoEditActivity.invoke(result);
            } else {
                JSONObject result = new JSONObject();
                result.put("cancel", true);
                callbackVideoEditActivity.invoke(result);
            }
            callbackVideoEditActivity = null;
        }
        else if (requestCode == REQUEST_CODE_CHOOSE_IMAGE && callbackChooseImage != null) {
            if(resultCode == Activity.RESULT_OK) {
                //?????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????
                ArrayList<Photo> resultPhotos = data.getParcelableArrayListExtra(EasyPhotos.RESULT_PHOTOS);
                boolean resultOriginal = data.getBooleanExtra(EasyPhotos.RESULT_SELECTED_ORIGINAL, false);
                if(resultPhotos == null) {
                    JSONObject jsonObject = new JSONObject();
                    jsonObject.put("success", false);
                    jsonObject.put("errMsg", "error");
                    callbackChooseImage.invoke(jsonObject);
                    return;
                }

                JSONArray tempFilePaths = new JSONArray();
                JSONArray tempFiles = new JSONArray();

                for (Photo p : resultPhotos) {
                    String path = p.path;
                    int orientation = BitmapUtils.getImageExifOrientation(path);

                    //???????????????????????????????????????????????????
                    if(!resultOriginal || compressImage) {
                        if(!path.endsWith(".gif")) {
                            String name = MD5Utils.md5(path);
                            Bitmap b = BitmapFactory.decodeFile(path);
                            File cacheFile = CacheUtils.getCachePath(context, "/choose-image-cache/", name);
                            if (b != null && cacheFile != null) {
                                try {
                                    if(compressImageWidth == 0 && compressImageHeight == 0) {
                                        compressImageWidth = p.width;
                                        compressImageHeight = p.height;
                                    }
                                    if(compressImageWidth > 0 || compressImageHeight > 0) {
                                        BitmapUtils.imageFixCompress(b,
                                                compressQuality,
                                                compressImageWidth,
                                                compressImageHeight,
                                                orientation + chooseImageImageDirectionCorrection,
                                                cacheFile);
                                        path = cacheFile.getAbsolutePath();
                                    }
                                } catch (Exception e) {
                                    e.printStackTrace();
                                    UniLogUtils.w("Failed to compress image \"" + path + "\"");
                                }
                            }
                            if (b != null)
                                b.recycle();
                        }
                    }

                    tempFilePaths.add(path);
                    JSONObject file = new JSONObject();
                    file.put("name", p.name);
                    file.put("size", p.size);
                    file.put("width", p.width);
                    file.put("height", p.height);
                    file.put("type", p.type);
                    file.put("duration", p.duration);
                    file.put("orientation", orientation);
                    file.put("time", p.time);
                    file.put("path", path);
                    tempFiles.add(file);
                }
                JSONObject jsonObject = new JSONObject();
                jsonObject.put("success", true);
                jsonObject.put("isOriginal", resultOriginal);
                jsonObject.put("tempFilePaths", tempFilePaths);
                jsonObject.put("tempFiles", tempFiles);
                jsonObject.put("errMsg", "ok");
                callbackChooseImage.invoke(jsonObject);
            } else {

                JSONObject jsonObject = new JSONObject();
                jsonObject.put("success", false);
                jsonObject.put("errMsg", "cancel");
                callbackChooseImage.invoke(jsonObject);
            }
            callbackChooseImage = null;
        }
        else if (requestCode == UCrop.REQUEST_CROP) {
            if(resultCode == Activity.RESULT_OK) {
                final Uri resultUri = UCrop.getOutput(data);
                if(resultUri != null) {
                    JSONObject jsonObject = new JSONObject();
                    jsonObject.put("tempFilePath", resultUri.getPath());
                    jsonObject.put("success", true);
                    jsonObject.put("errMsg", "ok");
                    callbackCropImage.invoke(jsonObject);
                }
            } else if (resultCode == UCrop.RESULT_ERROR) {
                JSONObject jsonObject = new JSONObject();
                jsonObject.put("success", false);
                final Throwable cropError = UCrop.getError(data);
                if(cropError != null)
                    jsonObject.put("errMsg", "error:" + cropError.getLocalizedMessage());
                else
                    jsonObject.put("errMsg", "error:unknow");
                callbackCropImage.invoke(jsonObject);
            }
        }
    }
}

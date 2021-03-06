package com.quickblox.qmunicate.utils;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.webkit.MimeTypeMap;

import com.nostra13.universalimageloader.cache.disc.naming.HashCodeFileNameGenerator;
import com.nostra13.universalimageloader.cache.memory.impl.UsingFreqLimitedMemoryCache;
import com.nostra13.universalimageloader.core.ImageLoaderConfiguration;
import com.nostra13.universalimageloader.core.decode.BaseImageDecoder;
import com.nostra13.universalimageloader.core.decode.ImageDecoder;
import com.nostra13.universalimageloader.core.decode.ImageDecodingInfo;
import com.quickblox.qmunicate.R;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;

public class ImageHelper {

    public static final int GALLERY_KITKAT_INTENT_CALLED = 2;
    public static final int GALLERY_INTENT_CALLED = 1;

    private static final String TEMP_FILE_NAME = "temp.png";

    private Activity activity;

    public ImageHelper(Activity activity) {
        this.activity = activity;
    }

    public static Bitmap getScaledBitmap(Bitmap bitmapOrg, int width, int height, int preferredWidth) {
        float scaleValue = ((float) preferredWidth) / width;
        Matrix matrix = new Matrix();
        matrix.postScale(scaleValue, scaleValue);
        Bitmap resizedBitmap = Bitmap.createBitmap(bitmapOrg, Consts.ZERO_INT_VALUE, Consts.ZERO_INT_VALUE,
                width, height, matrix, true);
        return resizedBitmap;
    }

    public static ImageLoaderConfiguration getImageLoaderConfiguration(Context context) {
        final int MEMORY_CACHE_LIMIT = 2 * 1024 * 1024;
        final int THREAD_POOL_SIZE = 5;
        final int COMPRESS_QUALITY = 60;
        final int MAX_IMAGE_WIDTH_FOR_MEMORY_CACHE = 250;
        final int MAX_IMAGE_HEIGHT_FOR_MEMORY_CACHE = 250;

        ImageLoaderConfiguration imageLoaderConfiguration = new ImageLoaderConfiguration.Builder(context)
                .memoryCacheExtraOptions(MAX_IMAGE_WIDTH_FOR_MEMORY_CACHE, MAX_IMAGE_HEIGHT_FOR_MEMORY_CACHE)
                .discCacheExtraOptions(MAX_IMAGE_WIDTH_FOR_MEMORY_CACHE, MAX_IMAGE_HEIGHT_FOR_MEMORY_CACHE,
                        Bitmap.CompressFormat.JPEG, COMPRESS_QUALITY, null).threadPoolSize(THREAD_POOL_SIZE)
                .threadPriority(Thread.NORM_PRIORITY).denyCacheImageMultipleSizesInMemory().memoryCache(
                        new UsingFreqLimitedMemoryCache(MEMORY_CACHE_LIMIT)).writeDebugLogs()
                .defaultDisplayImageOptions(Consts.UIL_DEFAULT_DISPLAY_OPTIONS).imageDecoder(
                        new SmartUriDecoder(context, new BaseImageDecoder(false)))
                .denyCacheImageMultipleSizesInMemory().discCacheFileNameGenerator(
                        new HashCodeFileNameGeneratorWithoutToken()).build();
        return imageLoaderConfiguration;
    }

    public static Bitmap getThumbnailFromVideo(String videoPath) {
        if (videoPath.contains("file://")) {
            videoPath = videoPath.replace("file://", "");
        }
        return ThumbnailUtils.createVideoThumbnail(videoPath, MediaStore.Video.Thumbnails.MINI_KIND);
    }

    public void getImage() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            Intent intent = new Intent();
            intent.setType("image/*");
            intent.setAction(Intent.ACTION_GET_CONTENT);
            activity.startActivityForResult(intent, GALLERY_INTENT_CALLED);
        } else {
            showKitKatGallery();
        }
    }

    private void showKitKatGallery() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        activity.startActivityForResult(intent, GALLERY_KITKAT_INTENT_CALLED);
    }

    public void showFullImage(Context context, String absolutePath) {
        Intent intent = new Intent();
        intent.setAction(android.content.Intent.ACTION_VIEW);
        Uri uri = Uri.parse("file://" + absolutePath);
        intent.setDataAndType(uri, "image/*");
        context.startActivity(intent);
    }

    public String getAbsolutePathByBitmap(Bitmap origBitmap) {
        File tempFile = new File(activity.getExternalFilesDir(null), "temp.png");
        ByteArrayOutputStream bos = null;
        FileOutputStream fos = null;
        try {
            bos = new ByteArrayOutputStream();
            origBitmap.compress(Bitmap.CompressFormat.PNG, Consts.ZERO_INT_VALUE, bos);
            byte[] bitmapData = bos.toByteArray();
            fos = new FileOutputStream(tempFile);
            fos.write(bitmapData);
            fos.close();
            bos.close();
        } catch (IOException e) {
            ErrorUtils.showError(activity, e);
        } finally {
            Utils.closeOutputStream(fos);
            Utils.closeOutputStream(bos);
        }
        return tempFile.getAbsolutePath();
    }

    public File getFileFromImageView(Bitmap origBitmap) throws IOException {
        final int preferredWidth = 300;
        int origWidth = origBitmap.getWidth();
        int origHeight = origBitmap.getHeight();
        File tempFile = new File(activity.getCacheDir(), TEMP_FILE_NAME);
        tempFile.createNewFile();
        Bitmap bitmap = getScaledBitmap(origBitmap, origWidth, origHeight, preferredWidth);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 0, bos);
        byte[] bitmapData = bos.toByteArray();
        FileOutputStream fos = new FileOutputStream(tempFile);
        fos.write(bitmapData);
        fos.close();
        bos.close();
        return tempFile;
    }

    /*
    * TODO SF class will be realised for video attach
     */
    public static class SmartUriDecoder implements ImageDecoder {

        private final BaseImageDecoder imageUriDecoder;

        private final Reference<Context> context;

        public SmartUriDecoder(Context context, BaseImageDecoder imageUriDecoder) {
            if (imageUriDecoder == null) {
                throw new NullPointerException("Image decoder can't be null");
            }

            this.context = new WeakReference(context);
            this.imageUriDecoder = imageUriDecoder;
        }

        @Override
        public Bitmap decode(ImageDecodingInfo info) throws IOException {
            if (TextUtils.isEmpty(info.getImageKey())) {
                return null;
            }

            String cleanedUriString = cleanUriString(info.getImageKey());
            if (isVideoUri(cleanedUriString)) {
                return makeVideoThumbnail(info.getTargetSize().getWidth(), info.getTargetSize().getHeight(),
                        cleanedUriString);
            } else {
                return imageUriDecoder.decode(info);
            }
        }

        private Bitmap makeVideoThumbnail(int width, int height, String filePath) {
            if (filePath == null) {
                return null;
            }
            Bitmap thumbnail = getThumbnailFromVideo(filePath);
            if (thumbnail == null) {
                return null;
            }

            Bitmap scaledThumb = scaleBitmap(thumbnail, width, height);
            thumbnail.recycle();

            addVideoIcon(scaledThumb);
            return scaledThumb;
        }

        private void addVideoIcon(Bitmap source) {
            Canvas canvas = new Canvas(source);
            Bitmap icon = BitmapFactory.decodeResource(context.get().getResources(), R.drawable.video_icon);

            float left = (source.getWidth() / 2) - (icon.getWidth() / 2);
            float top = (source.getHeight() / 2) - (icon.getHeight() / 2);

            canvas.drawBitmap(icon, left, top, null);
        }

        private boolean isVideoUri(String uri) {
            String fileExtension = MimeTypeMap.getFileExtensionFromUrl(uri);
            String mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(fileExtension);

            return mimeType == null ? false : mimeType.startsWith("video/");
        }

        private Bitmap scaleBitmap(Bitmap origBitmap, int width, int height) {
            float scale = Math.min(((float) width) / ((float) origBitmap.getWidth()),
                    ((float) height) / ((float) origBitmap.getHeight()));
            return Bitmap.createScaledBitmap(origBitmap, (int) (((float) origBitmap.getWidth()) * scale),
                    (int) (((float) origBitmap.getHeight()) * scale), false);
        }

        private String cleanUriString(String contentUriWithAppendedSize) {
            return contentUriWithAppendedSize.replaceFirst("_\\d+x\\d+$", "");
        }
    }

    private static class HashCodeFileNameGeneratorWithoutToken extends HashCodeFileNameGenerator {

        private static final String FACEBOOK_PATTERN = "https://graph.facebook.com/";
        private static final String TOKEN_PATTERN = "\\?token+=+.*";

        @Override
        public String generate(String imageUri) {
            if (imageUri.contains(FACEBOOK_PATTERN)) {
                return imageUri;
            }
            String replace = imageUri.replaceAll(TOKEN_PATTERN, "");
            return super.generate(replace);
        }
    }
}
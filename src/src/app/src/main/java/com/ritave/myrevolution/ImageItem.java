package com.ritave.myrevolution;

import android.graphics.Bitmap;

public class ImageItem {
    private Bitmap image;
    private String path;

    public ImageItem(Bitmap image, String path) {
        super();
        this.image = image;
        this.path = path;
    }

    public Bitmap getImage() {
        return image;
    }

    public void setImage(Bitmap image) {
        this.image = image;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }
}
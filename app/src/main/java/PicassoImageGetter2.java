import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.text.Html;
import android.util.Log;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.myapplication.R;
import com.squareup.picasso.Picasso;
import com.squareup.picasso.Target;

public class PicassoImageGetter2 implements Html.ImageGetter {

    private TextView textView;
    private Picasso picasso;
    private Resources res;

    public PicassoImageGetter2(@NonNull Picasso picasso, @NonNull TextView textView, @NonNull Resources res ) {
        this.picasso = picasso;
        this.textView = textView;
        this.res=res;
    }

    @Override
    public Drawable getDrawable(String source) {
        Log.d(PicassoImageGetter2.class.getName(), "Start loading url " + "https://questden.org" +source);

        BitmapDrawablePlaceHolder drawable = new BitmapDrawablePlaceHolder();

        picasso
                .load("https://questden.org" +source)
                .error(R.drawable.ic_eye)
                .into(drawable);

        return drawable;
    }

    private class BitmapDrawablePlaceHolder extends BitmapDrawable implements Target {

        protected Drawable drawable;

        @Override
        public void draw(final Canvas canvas) {
            if (drawable != null) {
              //  checkBounds();
                drawable.draw(canvas);
            }
        }

        public void setDrawable(@Nullable Drawable drawable) {
            if (drawable != null) {
                this.drawable = drawable;
            //    checkBounds();
                int width = drawable.getIntrinsicWidth();
                int height = drawable.getIntrinsicHeight();
                drawable.setBounds(0, 0, width, height);
                setBounds(0, 0, width, height);
                if (textView != null) {
                    textView.setText(textView.getText());
                }
            }
        }
/*
        private void checkBounds() {
            float defaultProportion = (float) drawable.getIntrinsicWidth() / (float) drawable.getIntrinsicHeight();
            int width = Math.min(textView.getWidth(), drawable.getIntrinsicWidth());
            int height = (int) ((float) width / defaultProportion);

            if (getBounds().right != textView.getWidth() || getBounds().bottom != height) {
                setBounds(0, 0, textView.getWidth(), height); //set to full width
                drawable.setBounds(
                        0, //centering an image
                        0,
                        width,
                        height);
                textView.setText(textView.getText()); //refresh text
            }
        }
 */

        //------------------------------------------------------------------//

        @Override
        public void onBitmapLoaded(Bitmap bitmap, Picasso.LoadedFrom from) {
            setDrawable(new BitmapDrawable(res, bitmap));
        }

        @Override
        public void onBitmapFailed(Exception e, Drawable errorDrawable) {

        }

        @Override
        public void onPrepareLoad(Drawable placeHolderDrawable) {
            setDrawable(placeHolderDrawable);
        }

        //------------------------------------------------------------------//

    }
}

/*
 * Based on java code by Yaser Rajabi https://github.com/yrajabi
 */
package de.dediggefedde.questden_blick_reader

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.text.Html.ImageGetter
import android.widget.TextView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.Request
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.target.SizeReadyCallback
import com.bumptech.glide.request.target.Target
import com.bumptech.glide.request.transition.Transition
import java.io.File
import java.lang.ref.WeakReference

/**
 * Image getter for .fromHTML call, turning <img> tags to their inline images.
 * This class uses Glide for image requests and prepends "https://questden.org" in front of src attr
 */
class GlideImageGetter(
    textView1: TextView,
    textView2: TextView,
    private val mContext: Context,
    private val sets: ModelSettings,
    private val matchParentWidth: Boolean = false,
    densityAware: Boolean = false
) : ImageGetter {
    private val container1: WeakReference<TextView> = WeakReference(textView1)
    private val container2: WeakReference<TextView> = WeakReference(textView2)
    private var density = 2f
    private val mMain: MainActivity = (mContext as MainActivity)

    init {
        if (densityAware) {
            container1.get()?.let {
                density = it.resources.displayMetrics.density
            }
            container2.get()?.let {
                density = it.resources.displayMetrics.density
            }
        }
    }

    override fun getDrawable(source: String): Drawable {
        var drawable1 = BitmapDrawablePlaceholder(container1)
        var drawable2 = BitmapDrawablePlaceholder(container2)
        var reqnam = "https://questden.org$source"

        val offImgPath = File(mMain.filesDir, "offline/${sets.curThreadId}_img")
        val imgNam = source.substringAfterLast("/") //offline mode
        if (offImgPath.exists() && File(offImgPath, imgNam).exists()) reqnam = "${mMain.filesDir}/offline/${sets.curThreadId}_img/$imgNam"

        val placeholderDrawable = BitmapDrawable(container1.get()?.resources, Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888))
        val placeholderDrawable2 = BitmapDrawable(container2.get()?.resources, Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888))

        // Load Image to the Drawable
        container1.get()?.apply {
            post {
                Glide.with(context)
                    .asBitmap()
                    .load(reqnam)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .placeholder(placeholderDrawable)
                    .into(drawable1)
            }
        }
        container2.get()?.apply {
            post {
                Glide.with(context)
                    .asBitmap()
                    .load(reqnam)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .placeholder(placeholderDrawable2)
                    .into(drawable2)
            }
        }

        return drawable2
    }

    private inner class BitmapDrawablePlaceholder(private val container:WeakReference<TextView>) : BitmapDrawable(container.get()?.resources, Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)), Target<Bitmap> {
        private var drawable: Drawable? = null
            set(value) {
                field = value
                value?.let { drawable ->
                    val drawableWidth = (drawable.intrinsicWidth * density).toInt()
                    val drawableHeight = (drawable.intrinsicHeight * density).toInt()
                    val maxWidth = container.get()!!.measuredWidth
                    if (drawableWidth > maxWidth || matchParentWidth) {
                        val calculatedHeight = maxWidth * drawableHeight / drawableWidth
                        drawable.setBounds(0, 0, maxWidth, calculatedHeight)
                        setBounds(0, 0, maxWidth, calculatedHeight)
                    } else {
                        drawable.setBounds(0, 0, drawableWidth, drawableHeight)
                        setBounds(0, 0, drawableWidth, drawableHeight)
                    }
                    container.get()?.text = container.get()?.text
                }
            }

        override fun draw(canvas: Canvas) {
            drawable?.draw(canvas)
        }

        override fun onLoadStarted(placeholderDrawable: Drawable?) {
            placeholderDrawable?.let {
                drawable = it
            }
        }

        override fun onLoadFailed(errorDrawable: Drawable?) {
            errorDrawable?.let {
                drawable = it
            }
        }

        override fun onResourceReady(bitmap: Bitmap, transition: Transition<in Bitmap>?) {
            drawable = BitmapDrawable(container.get()!!.resources, bitmap)
        }

        override fun onLoadCleared(placeholderDrawable: Drawable?) {
            placeholderDrawable?.let {
                drawable = it
            }
        }

        override fun getSize(sizeReadyCallback: SizeReadyCallback) {
            sizeReadyCallback.onSizeReady(Target.SIZE_ORIGINAL, Target.SIZE_ORIGINAL)
        }

        override fun removeCallback(cb: SizeReadyCallback) { // no-op
        }

        override fun setRequest(request: Request?) { // no-op
        }

        override fun getRequest(): Request? {
            return null
        }

        fun updateBitmap(newBitmap: Bitmap) {
            val updatedDrawable = BitmapDrawable(container.get()!!.resources, newBitmap).apply {
                setBounds(0, 0, newBitmap.width, newBitmap.height)
            }
            drawable = updatedDrawable
            container.get()?.text = container.get()?.text
        }

        override fun onStart() { // no-op
        }

        override fun onStop() { // no-op
        }

        override fun onDestroy() { // no-op
        }
    }

}
package de.dediggefedde.questden_blick_reader

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.text.Editable
import android.text.Html
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.*
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.view.marginLeft
import androidx.core.view.marginRight
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import org.jsoup.Jsoup
import org.xml.sax.XMLReader
import java.io.File
import java.lang.reflect.Field
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.HashMap
import kotlin.math.roundToInt

/**
 * RecyclerView custom adapter to display tgthread correctly
 *  currently has copy of tgthread, perhaps index/reference to external list better
 *  context given for image click zoom capabilities
 *  planned to have alternative compact layout
 *  need investigation for memory management
 */
class QuestDenListAdapter(val mContext: Context) :
    ListAdapter<TgPost, QuestDenListAdapter.ViewHolder>(DiffCallback()) {
    private val mMain: MainActivity = (mContext as MainActivity)
    private var displaySet: ModelSettings = ModelSettings()
    private var textSize: Float = 16f
    private lateinit var replyViewModel: ReplyViewModel

    interface ItemActionListener {
        fun openThread(url: String)
        fun removeOffline(mtg: TgPost)
        fun toggleWatch(mtg: TgPost)
        fun getWatched(threadId: String): Watch?
        fun getDownload(postID: String): OfflineThread?
        fun getIndexById(id: String): Int
        fun getSFWState(): SFWModes
    }

    var itemAction: ItemActionListener? = null
    inner class FullViewHolder(itemView: View) : ViewHolder(itemView)
    private class DiffCallback : DiffUtil.ItemCallback<TgPost>() {
        override fun areItemsTheSame(oldItem: TgPost, newItem: TgPost) =
            oldItem.postID == newItem.postID

        override fun areContentsTheSame(oldItem: TgPost, newItem: TgPost) =
            oldItem == newItem

    }
    init {
        setHasStableIds(true)
    }
    fun updateDisplaySetting(setting: ModelSettings?, txtSize: Float?) {
        if (setting !== null) displaySet = setting
        if (txtSize !== null) textSize = txtSize
    }
    override fun getItemId(position: Int): Long {
        return getItem(position).postID.toLongOrNull() ?: 0
    }

    /**
     * Viewholder item. made abstract to offer multiple layouts. currently only one used
     */
    abstract inner class ViewHolder(itemView: View) :
        RecyclerView.ViewHolder(itemView) {
        private val iVBinding =CommonBinding(itemView)// ListItemBinding.bind(itemView) // Nutze das richtige Layout-Binding
        private var mtg = TgPost()

        init {
            if (displaySet.listType != ThrdItemTyps.THREAD) {
                setListener()
            } else {
                setListenerThread()
            }
        }

        private fun setListenerThread() {
            val replLink={_:View->
                replyViewModel.insertAtCur(">>${mtg.postID}")
                MsgHelper.showMsg(mContext,">>${mtg.postID} copied into reply form.")
            }

            iVBinding.txDate.setOnClickListener(replLink)
            iVBinding.txTitle.setOnClickListener(replLink)
            iVBinding.txAuthor.setOnClickListener(replLink)

            iVBinding.imgUrl.setOnClickListener {
                mMain.viewImage(mtg)
            }
            iVBinding.txPostID.setOnClickListener {
                val openURL = Intent(Intent.ACTION_VIEW)
                val threadurl = mtg.url.replace(Regex("#\\d+"), "")
                openURL.data = Uri.parse("https://questden.org$threadurl#${mtg.postID}")
                it.context.startActivity(openURL)
            }
            iVBinding.txSummaryBottom.setOnClickListener {
                if (it.tag == "clickableClick") {
                    it.tag = ""
                } else {
                    mMain.toggleToolbarVisibility()
                }
            }
            iVBinding.txSummaryBottom.movementMethod = LinkMovementMethod.getInstance()
        }

        @SuppressLint("ClickableViewAccessibility")
        private fun setListener() {
            val evThreadTitleClick = View.OnClickListener {
                if (displaySet.listType != ThrdItemTyps.THREAD)
                    itemAction?.openThread(mtg.url)
            }
            iVBinding.txTitle.setOnClickListener(evThreadTitleClick)
            iVBinding.txAuthor.setOnClickListener(evThreadTitleClick)
            iVBinding.imgUrl.setOnClickListener {
                mMain.viewImage(mtg)
            }

            iVBinding.txWatch.setOnClickListener {
                if (mtg.url.indexOf("#") > 0) mtg.url = mtg.url.substring(0, mtg.url.indexOf("#"))
                if (displaySet.listType == ThrdItemTyps.OFFLINE) { //"delete" in offline mode
                    itemAction?.removeOffline(mtg)
                } else {
                    itemAction?.toggleWatch(mtg) //"watch/unwantch" in board/watch mode
                }
                adaptToThreadMode()
                itemView.invalidate()
            }
            iVBinding.txPostID.setOnClickListener {
                val openURL = Intent(Intent.ACTION_VIEW)
                val threadurl = mtg.url.replace(Regex("#\\d+"), "")
                openURL.data = Uri.parse("https://questden.org$threadurl#${mtg.postID}")
                it.context.startActivity(openURL)
            }
            iVBinding.txSummaryTop.setOnClickListener {
                if (displaySet.listType != ThrdItemTyps.THREAD) {
                    mtg.threadExtended = !mtg.threadExtended
                    if (mtg.threadExtended) floatSummary(thumbHeight = iVBinding.imgUrl.height, thumbWidth = iVBinding.imgUrl.width)
                    else bind(mtg)//notifyItemChanged(position)
                } else {
                    if (it.tag == "clickableClick") {
                        it.tag = ""
                    } else {
                        mMain.toggleToolbarVisibility()
                    }
                }
            }
            iVBinding.txSummaryBottom.setOnClickListener {
                if (displaySet.listType != ThrdItemTyps.THREAD) {
                    mtg.threadExtended = !mtg.threadExtended
                    if (mtg.threadExtended) floatSummary(thumbHeight = iVBinding.imgUrl.height, thumbWidth = iVBinding.imgUrl.width)
                    else bind(mtg)//notifyItemChanged(position)
                } else {
                    if (it.tag == "clickableClick") {
                        it.tag = ""
                    } else {
                        mMain.toggleToolbarVisibility()
                    }
                }
            }

            //click on links
            iVBinding.txSummaryTop.movementMethod = LinkMovementMethod.getInstance()
            iVBinding.txSummaryBottom.movementMethod = LinkMovementMethod.getInstance()

            adaptToThreadMode()
        }

        /**
         * Jsoup parse HTML code.
         * @param trimLength for trimming content after parsing
         */
        private fun removeHTML(str: String, trimLength: Int = 100): String {
            val doc = Jsoup.parse(str)
            val tex = doc.text()
            return if (tex.length > trimLength) tex.substring(0, trimLength) else tex
        }

        /**
         * adapts item to current watch-state (new posts, imgs, iswatched etc
         */
        open fun adaptToThreadMode() {
            iVBinding.txWatch.visibility = View.VISIBLE
            iVBinding.txNewPosts.visibility = View.VISIBLE
            iVBinding.txNewImg.visibility = View.VISIBLE

            when (displaySet.listType) {
                ThrdItemTyps.THREAD -> {
                    iVBinding.txWatch.visibility = View.GONE
                    iVBinding.txNewPosts.visibility = View.GONE
                    iVBinding.txNewImg.visibility = View.GONE
                }

                ThrdItemTyps.BOARD, ThrdItemTyps.WATCH -> { //watch is same display, but filtered only by watch
                    val w: Watch? = itemAction?.getWatched(mtg.postID)
//
                    if (w == null) { //not watched, can also show in watchlist when removing watch
                        iVBinding.txWatch.setTextColor(ContextCompat.getColor(mContext, R.color.color_watch_fontOff))
                        iVBinding.txWatch.text = mContext.getString(R.string.wBut_watch)
                        iVBinding.txNewPosts.visibility = View.GONE
                        iVBinding.txNewImg.visibility = View.GONE
                    } else {
                        iVBinding.txWatch.setTextColor(ContextCompat.getColor(mContext, R.color.color_watch_fontOn))
                        iVBinding.txWatch.text = mContext.getString(R.string.wBut_watched)
                        iVBinding.txNewPosts.visibility = View.VISIBLE
                        iVBinding.txNewImg.visibility = View.VISIBLE
                        iVBinding.txNewPosts.text = mContext.getString(R.string.NewPosts, w.newPosts)
                        iVBinding.txNewImg.text = mContext.getString(R.string.NewImg, w.newImg)
                    }
                }

                ThrdItemTyps.OFFLINE -> {
                    val offlineThread = itemAction?.getDownload(mtg.postID) ?: return
                    val date = Date(offlineThread.lastUpdate)
                    val formatter = SimpleDateFormat("yyyy-MM-dd\nHH:mm", Locale.getDefault())
                    val formattedTime = formatter.format(date)

                    iVBinding.txWatch.setTextColor(ContextCompat.getColor(mContext, R.color.color_watch_fontOff))
                    iVBinding.txWatch.text = mContext.getString(R.string.delete)
                    iVBinding.txNewPosts.text = formattedTime
                    iVBinding.txNewImg.visibility = View.GONE
                }
            }
        }

        fun bind(tg: TgPost) {
            mtg = tg
            if (mtg.url.indexOf("#") > 0) mtg.url = mtg.url.substring(0, mtg.url.indexOf("#"))

            adaptToThreadMode()

            iVBinding.txAuthor.visibility = if (mtg.author == "") View.GONE else View.VISIBLE
            iVBinding.txTitle.visibility = if (mtg.title == "") View.GONE else View.VISIBLE
            iVBinding.imgUrl.visibility = if (mtg.imgUrl == "") View.GONE else View.VISIBLE

            iVBinding.txSummaryTop.textSize = textSize
            iVBinding.txSummaryBottom.textSize = textSize

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) { //75% of phones
                iVBinding.txTitle.text = Html.fromHtml(mtg.title, Html.FROM_HTML_MODE_COMPACT)

                if (displaySet.listType == ThrdItemTyps.THREAD) {
                    val strbld = getHTMLStringBuilder(mtg.summary)
                    iVBinding.txSummaryTop.text = ""
                    iVBinding.txSummaryTop.visibility = View.GONE
                    iVBinding.txSummaryBottom.text = strbld
                    iVBinding.txSummaryBottom.visibility = View.VISIBLE
                } else {//board, watch, offline
                    iVBinding.txSummaryTop.text = removeHTML(mtg.summary) //condense, remove html, preview
                    iVBinding.txSummaryTop.visibility = View.VISIBLE

                    if (mtg.threadExtended) {
                        iVBinding.txNewImg.post {
                            floatSummary(thumbHeight = iVBinding.imgUrl.height, thumbWidth = iVBinding.imgUrl.width)
                        }
                    } else {
                        iVBinding.txSummaryBottom.text = "" //default next to image
                        iVBinding.txSummaryBottom.visibility = View.GONE
                    }
                }

                iVBinding.txAuthor.text = Html.fromHtml(mtg.author, Html.FROM_HTML_MODE_COMPACT)
            } else {
                iVBinding.txTitle.text = mtg.title
                val strbld = getHTMLStringBuilder(removeHTML(mtg.summary))
                iVBinding.txSummaryTop.text = ""
                iVBinding.txSummaryTop.visibility = View.GONE
                iVBinding.txSummaryBottom.text = strbld
                iVBinding.txAuthor.text = mtg.author
            }

            if (displaySet.listType == ThrdItemTyps.THREAD)
                iVBinding.txDate.text = tg.date
            else
                iVBinding.txDate.text = mContext.getString(R.string.posts, if (tg.postCount == 0) "<5" else tg.postCount)
            iVBinding.txPostID.text = mtg.postID

            if (!mtg.isHighlight) {
                iVBinding.linearLayout.setBackgroundColor(ContextCompat.getColor(mContext, R.color.color_list_bg))
            } else {
                iVBinding.linearLayout.setBackgroundColor(ContextCompat.getColor(mContext, R.color.color_list_high))
            }

            if (mtg.imgUrl != "") {
                loadImg(iVBinding.imgUrl)
            }
        }

        private fun loadImg(imageView: ImageView) {
            var imgUrl = "https://questden.org" + mtg.imgUrl
            if (mtg.isSpoiler && displaySet.sfw != SFWModes.NSFW) imgUrl = "https://questden.org/kusaba/spoiler.png"
            if(displaySet.thumbFromFull)imgUrl=imgUrl.replace("thumb", "src").replace("s.", ".")


            val imgNam = mtg.imgUrl.substringAfterLast("/") //offline mode
            val offImgPath = File(mMain.filesDir, "offline/${displaySet.curThreadId}_img")
            if (offImgPath.exists() && File(offImgPath, imgNam).exists()) imgUrl = "${mMain.filesDir}/offline/${displaySet.curThreadId}_img/$imgNam"

            Glide.with(imageView)
                .load(imgUrl)
                .listener(object : RequestListener<Drawable> {
                    override fun onLoadFailed(p0: GlideException?, p1: Any?, target: Target<Drawable>?, p3: Boolean): Boolean {
                        return false
                    }

                    override fun onResourceReady(p0: Drawable?, p1: Any?, target: Target<Drawable>?, p3: DataSource?, p4: Boolean): Boolean {
                        imageView.invalidate()
                        imageView.post {
                            if (displaySet.listType == ThrdItemTyps.THREAD) {
                                mMain.repeatScroll()
                            }
                        }
                        return false
                    }
                })
                .into(imageView)
        }

        fun SpannableStringBuilder.subSequenceWithSpans(start: Int, end: Int): SpannableStringBuilder {
            val subSequence = this.subSequence(start, end)
            val newBuilder = SpannableStringBuilder(subSequence)

            // Übertrage die Spans
            val spans = this.getSpans(start, end, Any::class.java)
            for (span in spans) {
                val spanStart = this.getSpanStart(span).coerceAtLeast(start) - start
                val spanEnd = this.getSpanEnd(span).coerceAtMost(end) - start
                newBuilder.setSpan(span, spanStart, spanEnd, this.getSpanFlags(span))
            }
            return newBuilder
        }

        //renders html and icons from input. Return contains clickable links/Spoilers etc.
        private fun getHTMLStringBuilder(html: String?, textView1: TextView = iVBinding.txSummaryTop, textView2: TextView = iVBinding.txSummaryBottom): SpannableStringBuilder {
            val tagHandler = HTMLTagHandler(mMain, textSize, itemAction) //rendeer HTML tags
            val imgGet = GlideImageGetter(textView1, textView2, mMain, displaySet) //render images/icons
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) { //75% of phones
                val sequenceTop: CharSequence = Html.fromHtml(convertToCustomTags(html), Html.FROM_HTML_MODE_COMPACT, imgGet, tagHandler)
                return SpannableStringBuilder(sequenceTop.trim { it == '\n' || it == '\r' })
            } else {
                return SpannableStringBuilder(html?.trim { it == '\n' || it == '\r' }) //no html rendering
            }
        }

        //divides text to txSummaryTop (next to image) and txSummaryBottom (below, full width)
        //called after images are loaded.
        private fun floatSummary(
            html: String? = mtg.summary,
            thumbHeight: Int = mtg.thumbHeight,
            thumbWidth: Int = iVBinding.txSummaryBottom.width
        ) { //, p0:BitmapDrawable?=iVBinding.imgUrl.drawable as BitmapDrawable
            if (html.isNullOrEmpty()) return

            val strbld = getHTMLStringBuilder(html)

            if (mtg.imgUrl.isEmpty()) {
                iVBinding.txSummaryBottom.text = strbld
                iVBinding.txSummaryTop.text = ""
                iVBinding.txSummaryBottom.visibility = View.VISIBLE
                iVBinding.txSummaryTop.visibility = View.GONE
                return
            }


            var topTexHeight = thumbHeight +
                    (iVBinding.txWatch.height.takeIf { iVBinding.txWatch.visibility != View.GONE } ?: 0) +
                    -(iVBinding.txTitle.height.takeIf { iVBinding.txTitle.text != "" } ?: 0) +
                    -(iVBinding.txAuthor.height.takeIf { iVBinding.txAuthor.text != "" } ?: 0)
            if (iVBinding.txNewImg.visibility == View.VISIBLE) topTexHeight += iVBinding.txNewImg.height + iVBinding.txNewPosts.height //iVBinding.barrier6.top-iVBinding.barrier4.top // (p0.bitmap.height.toFloat() * iVBinding.imgUrl.width.toFloat() / p0.bitmap.width.toFloat() -(iVBinding.barrier4.top-iVBinding.imgUrl.top)).toInt() //iVBinding.txSummaryTop.height
            val maxLines = (topTexHeight.toFloat() / iVBinding.txSummaryTop.lineHeight).roundToInt()
            val paint = iVBinding.txSummaryTop.paint

            if (maxLines <= 0 || topTexHeight < 0) {
                iVBinding.txSummaryBottom.text = strbld
                iVBinding.txSummaryTop.text = ""
                iVBinding.txSummaryBottom.visibility = View.VISIBLE
                iVBinding.txSummaryTop.visibility = View.GONE
                return
            }

            //check first 100 charactes for render-length
            val peakPos = minOf(200, strbld.length)
            val widthPerChar = paint.measureText(strbld.subSequence(0, peakPos).toString()) / peakPos

            iVBinding.txSummaryTop.visibility = View.VISIBLE
            iVBinding.txSummaryBottom.visibility = View.VISIBLE

            if (topTexHeight == 0 || widthPerChar == 0.0f) { //error in image size or too low: all in bottom
                iVBinding.txSummaryTop.text = ""
                iVBinding.txSummaryTop.visibility = View.GONE
                iVBinding.txSummaryBottom.text = strbld
            } else { //divide text top/bottom
                val totalWidth = iVBinding.txSummaryBottom.width.takeIf { it != 0 }
                    ?: (iVBinding.linearLayout.width - iVBinding.linearLayout.paddingLeft - iVBinding.linearLayout.paddingRight - iVBinding.txSummaryBottom.marginRight - iVBinding.txSummaryBottom.marginLeft)
                val availableWidth = totalWidth - thumbWidth//cachedTextTopWidth //iVBinding.txSummaryTop.width - iVBinding.txSummaryTop.paddingLeft - iVBinding.txSummaryTop.paddingRight
                val charsPerLine = availableWidth / widthPerChar
                val maxCharsInd = minOf((charsPerLine * maxLines).toInt(), strbld.length)
                var topText = strbld.subSequenceWithSpans(0, peakPos)

                if (strbld.length < maxCharsInd) { //whole text fits next to image
                    iVBinding.txSummaryTop.text = topText
                    iVBinding.txSummaryBottom.text = ""
                    iVBinding.txSummaryBottom.visibility = View.GONE
                    return
                }

                val canvas = Canvas()
                var lineStart = 0
                var yPos = 0f
                var bottomText: CharSequence = ""
                var mlines = maxLines

                while (lineStart < topText.length) {
                    var lineEnd = lineStart + paint.breakText(topText, lineStart, topText.length, true, availableWidth.toFloat(), null)

                    var currentLine = topText.substring(lineStart, minOf(lineEnd, topText.length))
                    val lineBreakIndex = currentLine.indexOf('\n')
                    if (lineBreakIndex != -1) lineEnd = lineStart + lineBreakIndex + 1 //brech beim ersten Zeilenumbruch um
                    else {
                        val lastSpaceIndex = currentLine.lastIndexOf(' ') //breche beim letzten Leerzeichen um
                        lineEnd = if (lastSpaceIndex != -1) lineStart + lastSpaceIndex + 1
                        else { //no space/linebreak to break text
                            lineStart //nothing in top
                        }
                    }
                    currentLine = topText.substring(lineStart, minOf(lineEnd, topText.length))

                    canvas.drawText(currentLine, 0f, yPos, paint)
                    yPos += iVBinding.txSummaryTop.lineHeight
                    lineStart = minOf(lineEnd, topText.length)

                    if (yPos > topTexHeight || (--mlines <= 0)) {
                        topText = strbld.subSequenceWithSpans(0, lineStart)
                        bottomText = strbld.removePrefix(topText.toString())
                        break
                    }
                }

                //if a tag starts at top, it might be ended prematurely. Seems to work (spoiler,link), though. formatting not checked
                //otherwise: move whole text to bottom to have working tags
//                val bottomText = strbld.removePrefix(topText)

                iVBinding.txSummaryTop.text = topText//bottomText.trim { it == '\n' || it == '\r' }// topText
                iVBinding.txSummaryBottom.text = bottomText.trim { it == '\n' || it == '\r' }
            }
        }

        private fun convertToCustomTags(str: String?): String {
            if (str == null) return ""
            var ret = str

            var rex = Regex("""<span style="font-size:small;">(.*?)</span>""", RegexOption.DOT_MATCHES_ALL)
            ret = rex.replace(ret) {
                "<span></span><CSmall>" + it.groupValues[1] + "</CSmall>"
            }
            rex = Regex("""<span style="font-family: Mona,'MS PGothic' !important;">(.*?)</span>""", RegexOption.DOT_MATCHES_ALL)
            ret = rex.replace(ret) {
                "<span></span><Caafont>" + it.groupValues[1] + "</Caafont>" //span needed for leading tags being recocnized
            }
            rex = Regex("""<span style="white-space: pre-wrap !important; font-family: monospace, monospace !important;">(.*?)</span>""", RegexOption.DOT_MATCHES_ALL)
            ret = rex.replace(ret) {
                "<span></span><CCode>" + it.groupValues[1] + "</CCode>" //span needed for leading tags being recocnized
            }
            rex = Regex("<span[^>]*?class=\"unkfunc\"[^>]*?>(.*?)</span>", RegexOption.DOT_MATCHES_ALL)
            ret = rex.replace(ret) {
                "<span></span><CQuote>" + it.groupValues[1] + "</CQuote>"
            }
            rex = Regex("<span[^>]*?class=\"spoiler\"[^>]*?>(.*?)</span>", RegexOption.DOT_MATCHES_ALL)
            ret = rex.replace(ret) {
                "<span></span><CSpoil>" + it.groupValues[1] + "</CSpoil>"
            }
            rex = Regex("<a[^>]*?href=\"(.*?)\"[^>]*?>(.*?)</a>", RegexOption.DOT_MATCHES_ALL)
            ret = rex.replace(ret) {
                "<span><CLink href='" + it.groupValues[2] + "'>" + it.groupValues[2] + "</CLink></span>"
            }

            rex = Regex("""<div[^>]*?>\s*?</div>\s*""")
            ret = rex.replace(ret, "")
            rex = Regex("""^\s*<br>""", RegexOption.DOT_MATCHES_ALL)
            ret = rex.replace(ret, "")
            ret = ret.replace(Regex("""<br>[\n\r\s]*<br>""", RegexOption.DOT_MATCHES_ALL), "<br /><mybr2><br /></mybr2>")
            ret = ret.replace("<br>", "<br /><mybr><br /></mybr>")

            return ret
        }
    }

    override fun getItemCount(): Int = currentList.size
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val refLayout = if (displaySet.listType == ThrdItemTyps.THREAD)
            when (displaySet.imageMode) {
                imgMode.FULL -> R.layout.list_item_imgfull
                imgMode.BIG -> R.layout.list_item_imgbig
                imgMode.SMALL -> R.layout.list_item
            } else R.layout.list_item
        val comView = inflater.inflate(refLayout, parent, false)

        replyViewModel = ViewModelProvider(mMain).get(ReplyViewModel::class.java)

        return FullViewHolder(comView)
    }

    override fun getItemViewType(position: Int): Int {
        return if (displaySet.listType == ThrdItemTyps.THREAD) displaySet.imageMode.ordinal + 1 else 0
    }


    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(currentList[position])
    }
}

/**
 * HTML tag handler for fromHTML, parsing special html tags
 *  Needed to have custom reactions on links and special css behavior
 *  realized by replacing regex with custom tags.
 *  More specifically:
 *   >>123 link jumps to questid,
 *   <span> with spoiler blackened until clicked
 *   redirect http-links to browser
 */
class HTMLTagHandler(
    private var mContext: Context,
    private var textsize: Float,
    private val itemAction: QuestDenListAdapter.ItemActionListener?
) :
    Html.TagHandler {
    private var startQuote = 0
    private var startSpoil = 0
    private var startSmall = 0
    private var startAA = 0
    private var startCode = 0
    private var startURL = 0
    private var startBR = 0
    private var startBR2 = 0
    private var curURL = ""
    private var spoiled = false
    private val attributes = HashMap<String, String>()

    private fun processAttributes(xmlReader: XMLReader) {
        try {
            val elementField: Field = xmlReader.javaClass.getDeclaredField("theNewElement")
            elementField.isAccessible = true
            val element: Any? = elementField.get(xmlReader)
            val attsField: Field? = element?.javaClass?.getDeclaredField("theAtts")
            attsField?.isAccessible = true
            val atts: Any? = attsField?.get(element)
            val dataField: Field? = atts?.javaClass?.getDeclaredField("data")
            dataField?.isAccessible = true

            val data = (dataField?.get(atts) as? Array<*>)?.filterIsInstance<String>()
            val lengthField: Field? = atts?.javaClass?.getDeclaredField("length")
            lengthField?.isAccessible = true
            val len = lengthField?.get(atts) as Int
            if (data != null)
                for (i in 0 until len)
                    attributes[data[i * 5 + 1]] = data[i * 5 + 4]
        } catch (e: java.lang.Exception) {
            val activity = mContext as? MainActivity
            activity?.findViewById<View>(android.R.id.content)?.let { rootView ->
                handleError(
                    mContext, "Parsing thread error",
                    e.message ?: "Unknown error parsing the thread", getCurrentStackTrace(), null
                )
            }
        }
    }

    override fun handleTag(
        opening: Boolean, tag: String, output: Editable,
        xmlReader: XMLReader
    ) {
        if (tag.equals("mybr", ignoreCase = true)) {
            if (opening) startBR = output.length
            if (!opening) output.setSpan(AbsoluteSizeSpan((textsize * 3f / 16f).toInt(), true), startBR, output.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        if (tag.equals("mybr2", ignoreCase = true)) {
            if (opening) startBR2 = output.length
            if (!opening) output.setSpan(AbsoluteSizeSpan((textsize * 12f / 16f).toInt(), true), startBR2, output.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        if (tag.equals("CQuote", ignoreCase = true)) {
            if (opening) startQuote = output.length
            if (!opening) output.setSpan(ForegroundColorSpan(ContextCompat.getColor(mContext, R.color.color_text_quote)), startQuote, output.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        if (tag.equals("CSmall", ignoreCase = true)) { //"small"=13px, "medium"=16px, "large"=18px official, but less noticable on mobile, hence 10 for small
            if (opening) startSmall = output.length
            if (!opening) output.setSpan(AbsoluteSizeSpan((textsize * 10f / 16f).toInt(), true), startSmall, output.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        if (tag.equals("Caafont", ignoreCase = true)) { //"small"=13px, "medium"=16px, "large"=18px official, but less noticable on mobile, hence 10 for small
            if (opening) startAA = output.length
            if (!opening) output.setSpan(TypefaceSpan("serif"), startAA, output.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        if (tag.equals("CCode", ignoreCase = true)) { //"small"=13px, "medium"=16px, "large"=18px official, but less noticable on mobile, hence 10 for small
            if (opening) startCode = output.length
            if (!opening) output.setSpan(TypefaceSpan("monospace"), startCode, output.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        if (tag.equals("CSpoil", ignoreCase = true)) {

            if (opening) {
                startSpoil = output.length
                spoiled = true
            } else {
                output.setSpan(Clickabl(URLSpan(""), true, Color.WHITE, mContext, itemAction), startSpoil, output.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                spoiled = false
            }
        }
        if (tag.equals("CLink", ignoreCase = true)) {
            if (opening) {
                processAttributes(xmlReader)
                curURL = attributes["href"].toString()
                startURL = output.length
            } else {
                var endp = output.substring(startURL).indexOf("</CLink>")
                if (endp == -1) endp = output.length - startURL
                output.setSpan(Clickabl(URLSpan(curURL), spoiled, Color.BLUE, mContext, itemAction), startURL, endp + startURL, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
    }
}

/**
 * Clickable text created by fromHTML and inserted by HTMLTagHandler
 *  spoiler (blackened) toggle readable
 *  http links opening browsers (to be done)
 */
class Clickabl(
    private var span: URLSpan?,
    private var spoiler: Boolean,
    private var colUnSpoil: Int,
    private var mContext: Context,
    private var itemAction: QuestDenListAdapter.ItemActionListener?
) : ClickableSpan() {
    private var spoiled = false


    override fun onClick(view: View) {
        val rexTag = Regex(">>(\\d+)$")
        view.tag = "clickableClick"

        if (span != null && rexTag.matches(span!!.url)) {

            val main = mContext as MainActivity
            val pos = itemAction?.getIndexById((rexTag.find(span!!.url)?.groupValues?.get(1) ?: 0).toString())
            if (pos != null) main.scrollHighlight(pos)
        } else if (span != null && !spoiler) {
            val openURL = Intent(Intent.ACTION_VIEW)
            openURL.data = Uri.parse(span!!.url)
            mContext.startActivity(openURL)
        }
        if (spoiler && itemAction?.getSFWState() == SFWModes.SFWQUESTION)
            spoiled = !spoiled

        view.invalidate()
    }

    override fun updateDrawState(ds: TextPaint) {
        if (spoiler) {
            if (itemAction?.getSFWState() != SFWModes.NSFW) {
                ds.color = if (spoiled) colUnSpoil else Color.BLACK
                ds.bgColor = Color.BLACK
            } else {
                ds.color = Color.BLACK
                ds.bgColor = Color.LTGRAY
            }
        } else {
            ds.color = Color.BLUE
            ds.bgColor = Color.TRANSPARENT
        }
    }
}

class CommonBinding(itemView: View) {
    val imgUrl: ImageView = itemView.findViewById(R.id.img_url)
    val txPostID: TextView = itemView.findViewById(R.id.tx_postID)
    val txSummaryBottom: TextView = itemView.findViewById(R.id.tx_Summary_Bottom)
    val txSummaryTop: TextView = itemView.findViewById(R.id.tx_Summary_Top)
    val txTitle: TextView = itemView.findViewById(R.id.tx_title)
    val txAuthor: TextView = itemView.findViewById(R.id.tx_author)
    val txWatch: TextView = itemView.findViewById(R.id.tx_watch)
    val txNewPosts: TextView = itemView.findViewById(R.id.tx_newPosts)
    val txNewImg: TextView = itemView.findViewById(R.id.tx_newImg)
    val txDate: TextView = itemView.findViewById(R.id.tx_date)
    val linearLayout: ConstraintLayout = itemView.findViewById(R.id.linearLayout)
}
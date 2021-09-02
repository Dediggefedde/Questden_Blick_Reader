package de.dediggefedde.questden_blick_reader

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.text.Editable
import android.text.Html
import android.text.Spannable
import android.text.TextPaint
import android.text.style.*
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.list_item.view.*
import org.jsoup.Jsoup
import org.xml.sax.XMLReader
import java.lang.reflect.Field

/**
 * RecyclerView custom adapter to display tgthread correctly
 *  currently has copy of tgthread, perhaps index/reference to external list better
 *  context given for image click zoom capabilities
 *  planned to have alternative compact layout
 *  need investigation for memory management
 */
class QuestDenListAdapter(val mContext: Context) :
    ListAdapter<TgThread, QuestDenListAdapter.ViewHolder>(DiffCallback()){
//class QuestDenListAdapter(var items: List<TgThread>, var mContext: Context) :
//    RecyclerView.Adapter<QuestDenListAdapter.ViewHolder>() {
    /**
     * custom viewholder for one tgthread object
     */
    inner class FullViewHolder(itemView: View) : ViewHolder(itemView)
    private class DiffCallback : DiffUtil.ItemCallback<TgThread>() {

        override fun areItemsTheSame(oldItem: TgThread, newItem: TgThread) =
            oldItem.postID == newItem.postID && oldItem.newImg == newItem.newImg && oldItem.newPosts==newItem.newPosts

        override fun areContentsTheSame(oldItem: TgThread, newItem: TgThread) =
            oldItem == newItem

    }
    /**
     * Viewholder item. made abstract to offer multiple layouts. currently only one used
     */
    abstract inner class ViewHolder(itemView: View) :
        RecyclerView.ViewHolder(itemView) {
        private var mTitleView: TextView? = null
        private var mSummaryView: TextView? = null
        private var mAuthorView: TextView? = null
        private var mImgView: ImageView? = null
        private var mWatchBut: TextView? = null
        private var mPostsNew: TextView? = null
        private var mImgNew: TextView? = null
        private var mNoView: TextView? = null
        private var mDate: TextView? = null
        private val mMain: MainActivity = (mContext as MainActivity)
        private var mtg = TgThread()

        init {
            //object access
            mTitleView = itemView.findViewById(R.id.tx_title)
            mSummaryView = itemView.findViewById(R.id.tx_Summary)
            mAuthorView = itemView.findViewById(R.id.tx_author)
            mImgView = itemView.findViewById(R.id.img_url)
            mWatchBut = itemView.findViewById(R.id.tx_watch)
            mPostsNew = itemView.findViewById(R.id.tx_newPosts)
            mImgNew = itemView.findViewById(R.id.tx_newImg)
            mNoView = itemView.findViewById(R.id.tx_postID)
            mDate = itemView.findViewById(R.id.tx_date)
            setListener()
            //event listeners
        }

        private fun setListener() {
            val evThreadTitleClick = View.OnClickListener {
                if (mtg.isThread)
                    mMain.displayThread(mtg.url, viewSingle = true,onlyCheckWatch = false)
            }
            mTitleView?.setOnClickListener(evThreadTitleClick)
            mAuthorView?.setOnClickListener(evThreadTitleClick)
            mImgView?.setOnClickListener {
                mMain.progressBarUndet.visibility = View.VISIBLE
                mMain.imageZoom.visibility = View.VISIBLE
                mMain.tx_img_path.visibility = View.VISIBLE
                var str = "https://questden.org" + mtg.imgUrl.replace("thumb", "src").replace("s.", ".")
                if (mtg.isSpoiler && mMain.sets.sfw == SFWModes.SFWREAL) str = "https://questden.org/kusaba/spoiler.png"


                Glide.with(mMain.imageZoom)
                    .load(str)
                    .listener(object : RequestListener<Drawable> {
                        override fun onLoadFailed(e: GlideException?, model: Any?, target: Target<Drawable>?, isFirstResource: Boolean): Boolean {
                            mMain.progressBarUndet.visibility = View.GONE
                            return false
                        }

                        override fun onResourceReady(
                            resource: Drawable?, model: Any?, target: Target<Drawable>?, dataSource: DataSource?, isFirstResource: Boolean
                        ): Boolean {
                            mMain.progressBarUndet.visibility = View.GONE
                            return false
                        }
                    })
                    .into(mMain.imageZoom)
                mMain.tx_img_path.text = str

            }
            mWatchBut?.setOnClickListener {
                if(mtg.url.indexOf("#")>0)mtg.url=mtg.url.substring(0,mtg.url.indexOf("#"))
                if (mMain.isWatched(mtg.url)) {
                    mMain.removeFromWatch(mtg.url)
                } else {
                    mMain.addToWatch(mtg)
                }
                updateWatchState()
                it.invalidate()
            }
            mNoView?.setOnClickListener {
                val openURL = Intent(Intent.ACTION_VIEW)
                val threadurl = mtg.url.replace(Regex("#\\d+"), "")
                openURL.data = Uri.parse("https://questden.org$threadurl#${mtg.postID}")
                it.context.startActivity(openURL)
            }
            mSummaryView?.setOnClickListener {
                if (mtg.isThread) mMain.setTextViewHTML(mSummaryView!!, mtg.summary)
                else {
                    if (it.tag == "clickableClick") {
                        it.tag = ""
                    } else {
                        if (mMain.toolbar.visibility == View.GONE) {
                            mMain.toolbar.visibility = View.VISIBLE
                            mMain.bottom_navigation.visibility = View.VISIBLE
                        } else {
                            mMain.toolbar.visibility = View.GONE
                            mMain.bottom_navigation.visibility = View.GONE
                            mMain.tool_dropout.visibility = View.GONE
                        }
                    }
                }
            }
            updateWatchState()
        }

        /**
         * Jsoup parse HTML code.
         * @param trimLength for trimming content after parsing
         */
        private fun getHTMLtext(str: String, trimLength: Int=100): String {
            val doc = Jsoup.parse(str)
            val tex = doc.text()
            return if (tex.length > trimLength) tex.substring(0, trimLength) else tex
        }

        /**
         * adapts item to current watch-state (new posts, imgs, iswatched etc
         */
        open fun updateWatchState() {
            if (mMain.isWatched(mtg.url)) {
                val w: Watch = mMain.getWatchByUrl(mtg.url)
                mtg.newPosts=w.newPosts
                mtg.newImg=w.newImg

                mWatchBut?.setTextColor(Color.parseColor("#FF37A523"))
                mWatchBut?.text = mContext.getString(R.string.wBut_watched)
                mPostsNew?.visibility = View.VISIBLE
                mImgNew?.visibility = View.VISIBLE
                mPostsNew?.text = mContext.getString(R.string.NewPosts, w.newPosts)
                mImgNew?.text = mContext.getString(R.string.NewImg, w.newImg)
            } else {
                mWatchBut?.setTextColor(Color.parseColor("#A52B23"))
                mWatchBut?.text = mContext.getString(R.string.wBut_watch)
                mPostsNew?.visibility = View.GONE
                mImgNew?.visibility = View.GONE
            }
        }

        /**
         * binds a thread object to an item for the recycle view
         * also sets visible, parses html, updates watch-state and fetches image
         * sets internally used mtg to the thread.
         */
        @SuppressLint("Range")
        fun bind(tg: TgThread) {
            mtg = tg
            if (mtg.url.indexOf("#") > 0) mtg.url = mtg.url.substring(0, mtg.url.indexOf("#"))

            if (mtg.isThread) {
                mWatchBut?.visibility = View.VISIBLE
                mPostsNew?.visibility = View.VISIBLE
                mImgNew?.visibility = View.VISIBLE
                updateWatchState()
            } else {
                mWatchBut?.visibility = View.GONE
                mPostsNew?.visibility = View.GONE
                mImgNew?.visibility = View.GONE
            }

            mAuthorView?.visibility = if (mtg.author == "") View.GONE else View.VISIBLE
            mTitleView?.visibility = if (mtg.title == "") View.GONE else View.VISIBLE
            mImgView?.visibility = if (mtg.imgUrl == "") View.GONE else View.VISIBLE

            mSummaryView?.textSize = mMain.sets.txsize

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) { //75% of phones
                mTitleView?.text = Html.fromHtml(mtg.title, Html.FROM_HTML_MODE_COMPACT)

                if (!mtg.isThread) mMain.setTextViewHTML(mSummaryView!!, mtg.summary)
                else mSummaryView?.text = getHTMLtext(mtg.summary)

                mAuthorView?.text = Html.fromHtml(mtg.author, Html.FROM_HTML_MODE_COMPACT)
            } else {
                mTitleView?.text = mtg.title
                mSummaryView?.text = mtg.summary
                mAuthorView?.text = mtg.author
            }

            mDate?.text = tg.date
            mNoView?.text = mtg.postID

            if (!mtg.isHighlight) {
                itemView.linearLayout?.setBackgroundColor(Color.parseColor("#F0E0D6"))
            } else {
                itemView.linearLayout?.setBackgroundColor(Color.parseColor("#F0C0B6"))
            }

            if (mtg.imgUrl != "" && mImgView != null) {
                var imgUrl = "https://questden.org" + mtg.imgUrl
                if (mtg.isSpoiler && mMain.sets.sfw != SFWModes.NSFW) imgUrl = "https://questden.org/kusaba/spoiler.png"

                //val imgwidth=(80 * getSystem().displayMetrics.density).toInt()

                Glide.with(mImgView)
                    .load(imgUrl)
                    //.apply(RequestOptions.overrideOf (imgwidth,Target.SIZE_ORIGINAL ))
                    .listener(object : RequestListener<Drawable> {
                        override fun onLoadFailed(p0: GlideException?, p1: Any?, target: Target<Drawable>?, p3: Boolean): Boolean {
                            return false
                        }

                        override fun onResourceReady(p0: Drawable?, p1: Any?, target: Target<Drawable>?, p3: DataSource?, p4: Boolean): Boolean {
                            //do something when picture already loaded
                            mImgView?.invalidate()
                            return false
                        }
                    })
                    .into(mImgView)

            }
        }
    }

    override fun getItemCount(): Int = currentList.size
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
//        if (viewType == 0) {
        val comView = inflater.inflate(R.layout.list_item, parent, false)
        return FullViewHolder(comView)
//        } else {
//            val comView = inflater.inflate(R.layout.list_item_compact, parent, false)
//            return CompactViewHolder(comView)
//        }
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
class HTMLTagHandler(private var mContext: Context) : Html.TagHandler {
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
            Toast.makeText(mContext, "There was an error parsing the Thread:\n${e.message}", Toast.LENGTH_SHORT).show()
           // Log.d("TAG", "Exception: $e")
        }
    }

    override fun handleTag(
        opening: Boolean, tag: String, output: Editable,
        xmlReader: XMLReader
    ) {
        if (tag.equals("mybr", ignoreCase = true)) {
            if (opening) startBR = output.length
            if (!opening) output.setSpan(AbsoluteSizeSpan(((mContext as MainActivity).sets.txsize * 3f / 16f).toInt(), true), startBR, output.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        if (tag.equals("mybr2", ignoreCase = true)) {
            if (opening) startBR2 = output.length
            if (!opening) output.setSpan(AbsoluteSizeSpan(((mContext as MainActivity).sets.txsize * 12f / 16f).toInt(), true), startBR2, output.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        if (tag.equals("CQuote", ignoreCase = true)) {
            if (opening) startQuote = output.length
            if (!opening) output.setSpan(ForegroundColorSpan(Color.parseColor("#789922")), startQuote, output.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        if (tag.equals("CSmall", ignoreCase = true)) { //"small"=13px, "medium"=16px, "large"=18px official, but less noticable on mobile, hence 10 for small
            if (opening) startSmall = output.length
            if (!opening) output.setSpan(AbsoluteSizeSpan(((mContext as MainActivity).sets.txsize * 10f / 16f).toInt(), true), startSmall, output.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
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
                output.setSpan(Clickabl(URLSpan(""), true, Color.WHITE, mContext), startSpoil, output.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
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
                output.setSpan(Clickabl(URLSpan(curURL), spoiled, Color.BLUE, mContext), startURL, endp + startURL, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
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
    private var mContext: Context
) : ClickableSpan() {
    private var spoiled = false
    override fun onClick(view: View) {
        val rexTag = Regex(">>(\\d+)$")
        view.tag = "clickableClick"

        if (span != null && rexTag.matches(span!!.url)) {
            val main = mContext as MainActivity
            val pos = main.listAdapt.currentList.indexOfFirst { it.postID == rexTag.find(span!!.url)?.groupValues?.get(1) ?: 0 }

            if (main.listAdapt.currentList.size < pos || pos == -1) return

            if (main.chronic.size > 0 && main.chronic[main.chronic.lastIndex].prop != pos.toString())
                main.chronic.add(Navis(NavOperation.LINK, pos.toString(), main.ingredients_list.layoutManager?.onSaveInstanceState()))

            main.scrollHighlight(pos)
            main.listAdapt.currentList.forEach { it.isHighlight = false }
            main.listAdapt.currentList[pos].isHighlight = true
            main.chronic.add(Navis(NavOperation.LINK, pos.toString(), main.ingredients_list.layoutManager?.onSaveInstanceState()))
//            main.listAdapt.notifyDataSetChanged(
        }else if(span!=null && !spoiler){
            val openURL = Intent(Intent.ACTION_VIEW)
            openURL.data = Uri.parse(span!!.url)
            mContext.startActivity(openURL)
        }
        if(spoiler && (mContext as MainActivity).sets.sfw == SFWModes.SFWQUESTION)
            spoiled = !spoiled

        view.invalidate()
    }

    override fun updateDrawState(ds: TextPaint) {
        if (spoiler) {
            if ((mContext as MainActivity).sets.sfw != SFWModes.NSFW) {
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

class SyncCompareListAdapter(var items_local: List<TgThread>, var items_remote: List<TgThread>) :
    RecyclerView.Adapter<SyncCompareListAdapter.ViewHolder>() {

    private var mLocalTitle: TextView? = null
    private var mRemoteTitle: TextView? = null
    private var mDirButton: ImageButton? = null
    private var mTitle:TextView?=null
//    private var mTransferState: Int=1 //3 state: 0 download, 1 ignore, 2 upload
    /**
     * custom viewholder
     */
    inner class FullViewHolder(itemView: View) : ViewHolder(itemView) {
//        init {
//        }
    }

    abstract inner class ViewHolder(itemView: View) :
        RecyclerView.ViewHolder(itemView) {
        init {
            mTitle=itemView.findViewById(R.id.sync_comp_title)
            mLocalTitle=itemView.findViewById(R.id.sync_comp_local_title)
            mRemoteTitle=itemView.findViewById(R.id.sync_comp_remote_title)
            mDirButton=itemView.findViewById(R.id.sync_comp_img)
        }
        fun bind(tg_local: TgThread?,tg_remote: TgThread?) {
            var titl=tg_local?.title?:""
            if(titl=="")titl=tg_remote?.title?:""

            mTitle?.text=titl
//            mLocalTitle?.text="local"
//            mRemoteTitle?.text="remote"
        }

    }
//    fun alignedItemPos(localPos:Int):Int{
//        return 0
//    }

    override fun getItemCount(): Int = items_local.size
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val comView = inflater.inflate(R.layout.sync_compare_item, parent, false)
        return FullViewHolder(comView)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items_local[position],items_local[position])
    }
}

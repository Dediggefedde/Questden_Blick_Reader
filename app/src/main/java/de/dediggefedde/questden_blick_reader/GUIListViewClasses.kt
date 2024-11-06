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
import android.widget.Toast
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import de.dediggefedde.questden_blick_reader.databinding.ActivityMainBinding
import de.dediggefedde.questden_blick_reader.databinding.ListItemBinding
import de.dediggefedde.questden_blick_reader.databinding.SyncCompareItemBinding
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
    private lateinit var binding: ActivityMainBinding
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
        private val iVBinding = ListItemBinding.bind(itemView) // Nutze das richtige Layout-Binding

        private val mMain: MainActivity = (mContext as MainActivity)
        private var mtg = TgThread()

        init {
            setListener()
        }

        @SuppressLint("ClickableViewAccessibility")
        private fun setListener() {
            val evThreadTitleClick = View.OnClickListener {
                if (mtg.isThread)
                    mMain.displayThread(mtg.url, viewSingle = true,onlyCheckWatch = false)
            }
            iVBinding.txTitle.setOnClickListener(evThreadTitleClick)
            iVBinding.txAuthor.setOnClickListener(evThreadTitleClick)
            iVBinding.imgUrl.setOnClickListener {
                binding.progressBarUndet.visibility = View.VISIBLE
                binding.imageZoom.visibility = View.VISIBLE
                binding.txImgPath.visibility = View.VISIBLE
                var str = "https://questden.org" + mtg.imgUrl.replace("thumb", "src").replace("s.", ".")
                if (mtg.isSpoiler && mMain.sets.sfw == SFWModes.SFWREAL) str = "https://questden.org/kusaba/spoiler.png"


                Glide.with(binding.imageZoom)
                    .load(str)
                    .listener(object : RequestListener<Drawable> {
                        override fun onLoadFailed(e: GlideException?, model: Any?, target: Target<Drawable>?, isFirstResource: Boolean): Boolean {
                            binding.progressBarUndet.visibility = View.GONE
                            return false
                        }

                        override fun onResourceReady(
                            resource: Drawable?, model: Any?, target: Target<Drawable>?, dataSource: DataSource?, isFirstResource: Boolean
                        ): Boolean {
                            binding.progressBarUndet.visibility = View.GONE
                            return false
                        }
                    })
                    .into(binding.imageZoom)
                binding.txImgPath.text = str

            }
            iVBinding.txWatch.setOnClickListener {
                if(mtg.url.indexOf("#")>0)mtg.url=mtg.url.substring(0,mtg.url.indexOf("#"))
                if (mMain.isWatched(mtg.url)) {
                    mMain.removeFromWatch(mtg.url)
                } else {
                    mMain.addToWatch(mtg)
                }
                updateWatchState()
                it.invalidate()
            }
            iVBinding.txPostID.setOnClickListener {
                val openURL = Intent(Intent.ACTION_VIEW)
                val threadurl = mtg.url.replace(Regex("#\\d+"), "")
                openURL.data = Uri.parse("https://questden.org$threadurl#${mtg.postID}")
                it.context.startActivity(openURL)
            }
            iVBinding.txSummary.setOnTouchListener  {_,ev->false}

            iVBinding.txSummary.setOnClickListener {
                if (mtg.isThread) mMain.setTextViewHTML(iVBinding.txSummary, mtg.summary)
                else {
                    if (it.tag == "clickableClick") {
                        it.tag = ""
                    } else {
                        mMain.toggleToolbarVisibility()
//                        if (binding.toolbar.visibility == View.GONE) {
//                            binding.toolbar.visibility = View.VISIBLE
//                            binding.bottomNavigation.visibility = View.VISIBLE
//                            binding.groupNavBut.visibility=View.VISIBLE
//                        } else {
//                            binding.toolbar.visibility = View.GONE
//                            binding.bottomNavigation.visibility = View.GONE
//                            binding.toolDropout.visibility = View.GONE
//                            binding.groupNavBut.visibility=View.GONE
//                        }
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

                iVBinding.txWatch.setTextColor(Color.parseColor("#FF37A523"))
                iVBinding.txWatch.text = mContext.getString(R.string.wBut_watched)
                iVBinding.txNewPosts.visibility = View.VISIBLE
                iVBinding.txNewImg.visibility = View.VISIBLE
                iVBinding.txNewPosts.text = mContext.getString(R.string.NewPosts, w.newPosts)
                iVBinding.txNewImg.text = mContext.getString(R.string.NewImg, w.newImg)
            } else {
                iVBinding.txWatch.setTextColor(Color.parseColor("#A52B23"))
                iVBinding.txWatch.text = mContext.getString(R.string.wBut_watch)
                iVBinding.txNewPosts.visibility = View.GONE
                iVBinding.txNewImg.visibility = View.GONE
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
                iVBinding.txWatch.visibility = View.VISIBLE
                iVBinding.txNewPosts.visibility = View.VISIBLE
                iVBinding.txNewImg.visibility = View.VISIBLE
                updateWatchState()
            } else {
                iVBinding.txWatch.visibility = View.GONE
                iVBinding.txNewPosts.visibility = View.GONE
                iVBinding.txNewImg.visibility = View.GONE
            }

            iVBinding.txAuthor.visibility = if (mtg.author == "") View.GONE else View.VISIBLE
            iVBinding.txTitle.visibility = if (mtg.title == "") View.GONE else View.VISIBLE
            iVBinding.imgUrl.visibility = if (mtg.imgUrl == "") View.GONE else View.VISIBLE

            iVBinding.txSummary.textSize = mMain.sets.txsize

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) { //75% of phones
                iVBinding.txTitle.text = Html.fromHtml(mtg.title, Html.FROM_HTML_MODE_COMPACT)

                if (!mtg.isThread) mMain.setTextViewHTML(iVBinding.txSummary, mtg.summary)
                else iVBinding.txSummary.text = getHTMLtext(mtg.summary)

                iVBinding.txAuthor.text = Html.fromHtml(mtg.author, Html.FROM_HTML_MODE_COMPACT)
            } else {
                iVBinding.txTitle.text = mtg.title
                iVBinding.txSummary.text = mtg.summary
                iVBinding.txAuthor.text = mtg.author
            }

            iVBinding.txDate.text = tg.date
            iVBinding.txPostID.text = mtg.postID

            if (!mtg.isHighlight) {
                iVBinding.linearLayout.setBackgroundColor(Color.parseColor("#F0E0D6"))
            } else {
                iVBinding.linearLayout.setBackgroundColor(Color.parseColor("#F0C0B6"))
            }

            if (mtg.imgUrl != "" ) {
                var imgUrl = "https://questden.org" + mtg.imgUrl
                if (mtg.isSpoiler && mMain.sets.sfw != SFWModes.NSFW) imgUrl = "https://questden.org/kusaba/spoiler.png"

                //val imgwidth=(80 * getSystem().displayMetrics.density).toInt()

                Glide.with(iVBinding.imgUrl)
                    .load(imgUrl)
                    //.apply(RequestOptions.overrideOf (imgwidth,Target.SIZE_ORIGINAL ))
                    .listener(object : RequestListener<Drawable> {
                        override fun onLoadFailed(p0: GlideException?, p1: Any?, target: Target<Drawable>?, p3: Boolean): Boolean {
                            return false
                        }

                        override fun onResourceReady(p0: Drawable?, p1: Any?, target: Target<Drawable>?, p3: DataSource?, p4: Boolean): Boolean {
                            //do something when picture already loaded
                            iVBinding.imgUrl.invalidate()
                            return false
                        }
                    })
                    .into(iVBinding.imgUrl)

            }
        }
    }

    override fun getItemCount(): Int = currentList.size
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val inflater = LayoutInflater.from(parent.context)

        binding = ActivityMainBinding.inflate(inflater)
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
class HTMLTagHandler(private var mContext: Context,private var binding: ActivityMainBinding) : Html.TagHandler {
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
                output.setSpan(Clickabl(URLSpan(""), true, Color.WHITE, mContext, binding), startSpoil, output.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
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
                output.setSpan(Clickabl(URLSpan(curURL), spoiled, Color.BLUE, mContext, binding), startURL, endp + startURL, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
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
    private var binding:ActivityMainBinding
) : ClickableSpan() {
    private var spoiled = false


    override fun onClick(view: View) {
        val rexTag = Regex(">>(\\d+)$")
        view.tag = "clickableClick"

        if (span != null && rexTag.matches(span!!.url)) {
            val main = mContext as MainActivity
            val pos = main.listAdapt.currentList.indexOfFirst { it.postID == (rexTag.find(span!!.url)?.groupValues?.get(1) ?: 0) }

            if (main.listAdapt.currentList.size < pos || pos == -1) return

            if (main.chronic.size > 0 && main.chronic[main.chronic.lastIndex].prop != pos.toString())
                main.chronic.add(Navis(NavOperation.LINK, pos.toString(), binding.ingredientsList.layoutManager?.onSaveInstanceState()))

            main.scrollHighlight(pos)
            main.listAdapt.currentList.forEach { it.isHighlight = false }
            main.listAdapt.currentList[pos].isHighlight = true
            main.chronic.add(Navis(NavOperation.LINK, pos.toString(), binding.ingredientsList.layoutManager?.onSaveInstanceState()))
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

class SyncCompareListAdapter(var itemsLocal: List<TgThread>, var itemsRemote: List<TgThread>) :
    RecyclerView.Adapter<SyncCompareListAdapter.ViewHolder>() {
        private lateinit var syncComItemBinding:SyncCompareItemBinding

    /**
     * custom viewholder
     */
    inner class FullViewHolder(itemView: View) : ViewHolder(itemView) {
//        init {
//        }
    }

    abstract inner class ViewHolder(itemView: View) :
        RecyclerView.ViewHolder(itemView) {

        fun bind(tgLocal: TgThread?, tgRemote: TgThread?) {
            var titl=tgLocal?.title?:""
            if(titl=="")titl=tgRemote?.title?:""

            syncComItemBinding.syncCompTitle.text=titl
//            mLocalTitle?.text="local"
//            mRemoteTitle?.text="remote"
        }

    }

    override fun getItemCount(): Int = itemsLocal.size
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        syncComItemBinding = SyncCompareItemBinding.inflate(inflater)
        val comView = inflater.inflate(R.layout.sync_compare_item, parent, false)
        return FullViewHolder(comView)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(itemsLocal[position],itemsLocal[position])
    }
}

package de.dediggefedde.questden_blick_reader

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Parcelable
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSmoothScroller
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import de.dediggefedde.questden_blick_reader.databinding.FragmentMainBinding
import java.io.File
import kotlin.math.abs

class MainFragment : Fragment() {
    private lateinit var viewModel: DataViewModel
    var listAdapt: QuestDenListAdapter?=null
    private var curViewedInd = 0 //index of current view item (top) of displayDataList

    private var scrollMode = ScrollMode.IMAGES //next/prev got to next img or post

    lateinit var binding: FragmentMainBinding
    private lateinit var scrollListener: RecyclerView.OnScrollListener
    var autoscroll = false
    var atWatchPosition=-1

    inner class TopSnappingScroller(context: Context) : LinearSmoothScroller(context) {
        override fun getVerticalSnapPreference(): Int {
            return SNAP_TO_START // Setze den Snap-Preference auf den oberen Rand
        }

        override fun calculateTimeForScrolling(dx: Int): Int {
            val time = super.calculateTimeForScrolling(dx)
            return time * 2 // Verdopple die Scrollzeit
        }
    }
    fun showToolbar(show:Boolean){
        if(!show)binding.toolDropout.visibility=View.GONE
        binding.bottomNavigation.visibility=if(show)View.VISIBLE else View.GONE
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel = ViewModelProvider(requireActivity()).get(DataViewModel::class.java)
        listAdapt = QuestDenListAdapter(requireContext())

        binding = FragmentMainBinding.bind(view)
        binding.postListRecView.layoutManager = LinearLayoutManager(requireContext())
        binding.postListRecView.adapter = listAdapt

        binding.progressBarDet.visibility = View.GONE
        binding.progressBarUndet.visibility = View.GONE
        binding.imageZoom.visibility = View.GONE
        binding.txImgPath.visibility = View.GONE

        setRecyclerViewScrollListener()
        addListviewEvents()
        addEventListeners()
        addObservers()

        viewImage(viewModel.fullViewImg)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_main, container, false)
    }

    private fun addEventListeners() {
        binding.imageZoom.setOnClickListener {
            binding.imageZoom.visibility = View.GONE
            binding.txImgPath.visibility = View.GONE
            viewModel.fullViewImg=null
        }
        binding.btnOpenFont.setOnClickListener { btnOpenTools() }
        binding.btnFirst.setOnClickListener { btnFirstButton() }
        binding.btnLast.setOnClickListener { btnLastButton() }
        binding.btnNext.setOnClickListener { btnNextButton() }
        binding.btnPrev.setOnClickListener { btnPrevButton() }
        binding.txImgPath.setOnClickListener{btnimgZoomPath()}
        binding.btnToggleSFW.setOnClickListener { btnTglSFW() }
        binding.btnIncFont.setOnClickListener { btnIncFont() }
        binding.btnDecFont.setOnClickListener { btnDecFont() }
        binding.btnUpdate.setOnClickListener { btnUpdateButton() }
        binding.btnOnlyPics.setOnClickListener { btnToggleOnlyPictures() }
        binding.txPosition.setOnClickListener { btnSkipModeChange() }
        binding.btnOffline.setOnClickListener { btnTglOffline() }
        binding.btnWatch.setOnClickListener {
            viewModel.toggleWatch()
            updateWatchImg()
            if(viewModel.getWatched()!=null)
                Toast.makeText(requireContext(), "Thread added to watchlist!", Toast.LENGTH_SHORT).show()
            else
                Toast.makeText(requireContext(), "Thread removed from watchlist!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun addListviewEvents() {
        listAdapt?.itemAction = object : QuestDenListAdapter.ItemActionListener {
            override fun openThread(url: String) {
                viewModel.loadThread(url, ThrdItemTyps.THREAD)
            }

            override fun toggleWatch(mtg: TgPost) {
                viewModel.toggleWatch(mtg.postID)
                atWatchPosition = if(viewModel.getWatched(mtg.postID)!=null) viewModel.getPositionById(mtg.postID) else -1
                updateWatchImg()
            }

            override fun removeOffline(mtg: TgPost) {
                val htmlFile = File(requireContext().applicationContext.filesDir, "offline/${mtg.postID}.html")
                viewModel.deleteOffline(mtg.postID)
                viewModel.loadCurThread() //update thread
            }

            override fun getWatched(threadId: String): Watch? {
                return viewModel.getWatched(threadId)
            }

            override fun getDownload(postID: String): OfflineThread? {
                return viewModel.getDownload(postID)
            }

            override fun getIndexById(id: String): Int {
                return listAdapt?.currentList?.indexOfFirst { it.postID == id }?:-1
            }

            override fun getSFWState(): SFWModes {
                return viewModel.sets.sfw
            }
        }
    }

    private fun setRecyclerViewScrollListener() {
        scrollListener = object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                curViewedInd = (binding.postListRecView.layoutManager as LinearLayoutManager).findFirstVisibleItemPosition()
                updatePositionDisplay()
                autoscroll = false
            }

            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                if (!autoscroll) curViewedInd = (binding.postListRecView.layoutManager as LinearLayoutManager).findFirstVisibleItemPosition()
                updatePositionDisplay()
            }
        }
        binding.postListRecView.addOnScrollListener(scrollListener)
    }

    fun repeatScroll() {
        if (!autoscroll) return
//        val pos = (binding.postListRecView.layoutManager as LinearLayoutManager).findFirstVisibleItemPosition()
        val smoothScroller = TopSnappingScroller(binding.postListRecView.context)
        smoothScroller.targetPosition = curViewedInd
        (binding.postListRecView.layoutManager as LinearLayoutManager).startSmoothScroll(smoothScroller)
    }

    fun scrollHighlight(pos: Int,backwards:Boolean=false) {
        if (!viewModel.hasIndex(pos)) return

        if(!backwards) viewModel.displayList.value?.get(curViewedInd)?.postID?.let { postID ->
            viewModel.backLinkStack.add(postID)
        }
        val lasthighInd = viewModel.highLightInd
        viewModel.setHighlight(pos)

        var vholder = binding.postListRecView.findViewHolderForAdapterPosition(lasthighInd)
        vholder?.itemView?.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.color_list_bg))

        autoscroll = true
        val smoothScroller = TopSnappingScroller(binding.postListRecView.context)
        smoothScroller.targetPosition = pos

        val layoutManag = (binding.postListRecView.layoutManager as LinearLayoutManager)
        val firstvisiblePos = layoutManag.findFirstVisibleItemPosition()

        if (abs(firstvisiblePos - pos) < 10) (binding.postListRecView.layoutManager as LinearLayoutManager).startSmoothScroll(smoothScroller)
        else {
            layoutManag.scrollToPositionWithOffset(pos, 0)
        }
        curViewedInd = pos
        updatePositionDisplay()

        // Mark the new highlight
        val handler = Handler(Looper.getMainLooper())
        handler.postDelayed({
            vholder = binding.postListRecView.findViewHolderForAdapterPosition(pos)
            vholder?.itemView?.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.color_list_high))
        }, 250)
    }

    //fullview image
    fun viewImage(mtg: TgPost?) {
        if(mtg===null)return
        binding.progressBarUndet.visibility = View.VISIBLE
        binding.imageZoom.visibility = View.VISIBLE
        binding.txImgPath.visibility = View.VISIBLE
        viewModel.fullViewImg=mtg
        var str = "https://questden.org" + mtg.imgUrl.replace("thumb", "src").replace("s.", ".")
        if (mtg.isSpoiler && viewModel.sets.sfw == SFWModes.SFWREAL) str = "https://questden.org/kusaba/spoiler.png"

        binding.txImgPath.text = str

        val imgNam = mtg.imgUrl.substringAfterLast("/").replace("thumb", "src").replace("s.", ".")
        val offImgPath = File(requireContext().applicationContext.filesDir, "offline/${viewModel.sets.curThreadId}_img")
        if (offImgPath.exists() && File(offImgPath, imgNam).exists()) str = "${requireContext().applicationContext.filesDir}/offline/${viewModel.sets.curThreadId}_img/$imgNam"

        Glide.with(binding.imageZoom).asDrawable().load(str).listener(object : RequestListener<Drawable> {
                override fun onLoadFailed(e: GlideException?, model: Any?, target: Target<Drawable>?, isFirstResource: Boolean): Boolean {
                    binding.progressBarUndet.visibility = View.GONE
                    return false
                }

                override fun onResourceReady(
                    resource: Drawable?, model: Any?, target: Target<Drawable>?, dataSource: DataSource?, isFirstResource: Boolean
                ): Boolean {
                    // Manuell die Größe des ImageViews festlegen
                    binding.progressBarUndet.visibility = View.GONE
                    return false
                }
            }).into(binding.imageZoom)
    }

    private fun showOfflineConfirmDialog(threadId: String) {
        val htmlFile = File(requireContext().applicationContext.filesDir, "offline/${threadId}.html")
        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle("Confirm Action")
        if (htmlFile.exists()) {
            builder.setMessage("Do you want to delete or update local data?")
            builder.setPositiveButton("Delete") { dialog, _ ->
                viewModel.deleteOffline(threadId)
                viewModel.loadCurThread() //update thread
                dialog.dismiss()
            }
            builder.setNeutralButton("Update") { dialog, _ ->
                viewModel.loadThread(viewModel.sets.curURL, mode = ThrdItemTyps.THREAD, updOffline = true) //triggers update after refresh
                dialog.dismiss()
            }
            builder.setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
        } else {
            builder.setMessage("Do you want to download the thread and ${viewModel.getImageListSize()} images?")
            builder.setPositiveButton("Download") { dialog, _ ->
                viewModel.writeToOffline(threadId,false)
                viewModel.downloadImages(threadId,false)
                dialog.dismiss()
            }
            builder.setNeutralButton("Only thumbnails") { dialog, _ ->
                viewModel.writeToOffline(threadId,true)
                viewModel.downloadImages(threadId,true)
                dialog.dismiss()
            }
            builder.setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
        }
        builder.create().show()
    }

    private fun btnTglOffline(threadId:String=viewModel.sets.curThreadId) {
        showOfflineConfirmDialog(threadId)
    }


    /**
     * updates scroll position display at bottom
     */
    fun updatePositionDisplay() {
        var posMod = "Page: "
        val curPos: Int
        var maxPos: Int

        if(viewModel.getDisplayListSize()==0){//empty offline/watch list
            maxPos = 1
            curPos =1
            posMod = "Post:\n"
        }
        else if (!viewModel.hasIndex(curViewedInd))
            return //something wrong
        else if (viewModel.sets.listType == ThrdItemTyps.THREAD) {
            if (scrollMode == ScrollMode.IMAGES || viewModel.sets.showOnlyPics) {
                maxPos = viewModel.getImageListSize()
                curPos = viewModel.getItemImageCnt(curViewedInd) //displayDataList.take(curViewedInd).filter { it.imgUrl != "" }.size
                posMod = "Image:\n"
            } else {
                maxPos = viewModel.getDisplayListSize()
                curPos = curViewedInd + 1
                posMod = "Post:\n"
            }
            viewModel.updateCurReadId(curViewedInd)
        } else {
            maxPos = viewModel.sets.curMaxPage + 1
            curPos = viewModel.sets.boardPage + 1
            if (maxPos < curPos) maxPos = curPos //current page = maxpage, link missing
        }

        binding.txPosition.text = getString(R.string.CurPos, posMod, curPos, maxPos)
    }

    private fun addObservers() {
        val scrolling = {
            (binding.postListRecView.layoutManager as LinearLayoutManager)
                .scrollToPositionWithOffset(viewModel.getLastReadIndex().takeIf { it >= 0 } ?: 0, 0)
            updatePositionDisplay()
        }

        viewModel.displayList.observe(viewLifecycleOwner) { list ->
            list?.let {
                listAdapt?.updateDisplaySetting(viewModel.sets, txtSize = viewModel.sets.txsize)
                listAdapt?.submitList(it, scrolling)
                updateOfflineImg()
                updateWatchImg()
                if (viewModel.fromOffline) Toast.makeText(requireContext(), "Loaded offline data from storage for ${viewModel.sets.curThreadId}.html", Toast.LENGTH_SHORT).show()
            }
        }

        viewModel.setsLiveData.observe(viewLifecycleOwner) { set ->

            listAdapt?.updateDisplaySetting(set, txtSize = viewModel.sets.txsize)

            binding.btnToggleSFW.text = when (set.sfw) {
                SFWModes.SFWQUESTION -> getString(R.string.SFWQuestion)
                SFWModes.SFWREAL -> getString(R.string.SFW)
                SFWModes.NSFW -> getString(R.string.NSFW)
            }

            //Scrollmode image
            val imgid = if (set.showOnlyPics) R.drawable.ic_exclnonimg else R.drawable.ic_inclnonimg
            binding.btnOnlyPics.setImageDrawable(ContextCompat.getDrawable(requireContext(), imgid))

            //setup buttons/menus for different views
            when (viewModel.sets.listType) {
                ThrdItemTyps.BOARD, ThrdItemTyps.WATCH, ThrdItemTyps.OFFLINE -> {
                    binding.btnOnlyPics.visibility = View.GONE
                    binding.btnToggleSFW.visibility = View.GONE
                    binding.btnOffline.visibility = View.GONE
                    binding.btnWatch.visibility = View.GONE
                }

                ThrdItemTyps.THREAD -> {
                    binding.btnOnlyPics.visibility = View.VISIBLE
                    binding.btnToggleSFW.visibility = View.VISIBLE
                    binding.btnOffline.visibility = View.VISIBLE
                    binding.btnWatch.visibility = View.VISIBLE
                }
            }
        }

        viewModel.reqProgLive.observe(viewLifecycleOwner) { prog ->
            when (prog.status) {
                ProgStatus.RUNNING -> {
                    val perc = (prog.pos * 100f / prog.max).toInt()
                    binding.progressBarUndet.visibility = View.VISIBLE
                    binding.progressBarDet.visibility = View.VISIBLE
                    binding.progressBarDet.progress = perc
                }

                ProgStatus.DONE -> {
                    binding.progressBarUndet.visibility = View.GONE
                    binding.progressBarDet.visibility = View.GONE
                    binding.progressBarUndet.progress = 0
                    binding.progressBarDet.progress = 0
                    prog.status = ProgStatus.IDLE
                    if(atWatchPosition!=-1) {
                        listAdapt?.notifyItemChanged(atWatchPosition)
                        atWatchPosition=-1
                    }
                }

                ProgStatus.ERROR -> {
                    binding.progressBarUndet.visibility = View.GONE
                    binding.progressBarDet.visibility = View.GONE
                    binding.progressBarUndet.progress = 0
                    binding.progressBarDet.progress = 0

                    handleError(requireContext().applicationContext,"Loading Thread error",
                        prog.msg, getCurrentStackTrace(), viewModel)
                    prog.status = ProgStatus.IDLE
                }

                ProgStatus.IDLE -> {
                    binding.progressBarUndet.visibility = View.GONE
                    binding.progressBarDet.visibility = View.GONE
                    binding.progressBarUndet.progress = 0
                    binding.progressBarDet.progress = 0
                }
            }
        }

        viewModel.downProgLive.observe(viewLifecycleOwner) { prog ->
            when (prog.status) {
                ProgStatus.RUNNING -> {
                    val perc = (prog.pos * 100f / prog.max).toInt()
                    binding.progressBar.visibility = View.VISIBLE
                    binding.progressText.visibility = View.VISIBLE
                    binding.progressBar.progress = perc
                    binding.progressText.text = getString(R.string.downloaded_of_images, prog.pos, prog.max)
                }

                ProgStatus.DONE -> {
                    binding.progressBar.visibility = View.GONE
                    binding.progressText.visibility = View.GONE
                    updateOfflineImg()
                    Toast.makeText(requireContext().applicationContext, getString(R.string.all_images_downloaded), Toast.LENGTH_SHORT).show()
                    prog.status = ProgStatus.IDLE
                }

                ProgStatus.ERROR -> {
                    binding.progressBar.visibility = View.GONE
                    binding.progressText.visibility = View.GONE
                    updateOfflineImg()
                    prog.status = ProgStatus.IDLE

                    handleError(requireContext().applicationContext,"Downloading thread error",
                        prog.msg, getCurrentStackTrace(), viewModel)
                }

                ProgStatus.IDLE -> {
                    binding.progressBar.visibility = View.GONE
                    binding.progressText.visibility = View.GONE
                    updateOfflineImg()
                }
            }
        }
    }

    private fun updateOfflineImg() {
        if (viewModel.isDownloaded()) {
            binding.btnOffline.setImageDrawable(ContextCompat.getDrawable(requireContext(), R.drawable.online))
        } else {
            binding.btnOffline.setImageDrawable(ContextCompat.getDrawable(requireContext(), R.drawable.offline))
        }
    }
    private fun updateWatchImg() {
        if (viewModel.getWatched()!==null) {
            binding.btnWatch.setImageDrawable(ContextCompat.getDrawable(requireContext(), R.drawable.eye_open))
        } else {
            binding.btnWatch.setImageDrawable(ContextCompat.getDrawable(requireContext(), R.drawable.eye_closed))
        }
    }

    /*
     * button click event handlers below
     * view is needed even not used, otherwise crash
     * updates current page list and all watched thread numbers
     */

    /**
     * path text of fullview-image. Opens image link in browser
     */
    private fun btnimgZoomPath() {
        val openURL = Intent(Intent.ACTION_VIEW)

        val targeturl = binding.txImgPath.text
        openURL.data = Uri.parse(targeturl.toString())
        startActivity(openURL)
    }

    /**
     * changes mode display and skip for next/prev
     * Only changes display
     */
    private fun btnSkipModeChange() {
        scrollMode = if (scrollMode == ScrollMode.ALL) ScrollMode.IMAGES else ScrollMode.ALL
        updatePositionDisplay()
    }

    /**
     * button toggle sfw mode
     * autoload spoiler images (yes,no, question = only on click)
     * click order SFW?→SFW→NSFW→SFW?
     */
    @SuppressLint("NotifyDataSetChanged")
    fun btnTglSFW() {
        viewModel.rotateSFW()
        listAdapt?.updateDisplaySetting(viewModel.sets, txtSize = viewModel.sets.txsize)
        listAdapt?.notifyDataSetChanged()

        Toast.makeText(requireContext(), "SFW mode set to ${viewModel.sets.sfw.displayName}", Toast.LENGTH_SHORT).show()
    }

    /**
     * opens/closes navigation tool sections
     */
    private fun btnOpenTools() {
        if (binding.toolDropout.visibility != View.GONE) {
            binding.toolDropout.visibility = View.GONE
        } else {
            binding.toolDropout.visibility = View.VISIBLE
        }
    }

    /**
     * button event for change font size
     */
    @SuppressLint("NotifyDataSetChanged")
    fun btnIncFont() {
        viewModel.sets.txsize += 1f
        listAdapt?.updateDisplaySetting(null, txtSize = viewModel.sets.txsize)
        listAdapt?.notifyDataSetChanged()
    }

    /**
     * button event for change font size
     */
    @SuppressLint("NotifyDataSetChanged")
    fun btnDecFont() {
        viewModel.sets.txsize -= 1f
        listAdapt?.updateDisplaySetting(null, txtSize = viewModel.sets.txsize)
        listAdapt?.notifyDataSetChanged()
    }

    /**
     * update button, refreshes page, fetches updates when on watchlist
     */
    private fun btnUpdateButton() {
        if (viewModel.fromOffline) //if offline data, call to delete/update/cancel
            btnTglOffline()
        else viewModel.loadCurThread()
    }

    /**
     * picture btn click
     * toggles showing all vs only posts with pictures
     */
    private fun btnToggleOnlyPictures() {
        val newMode = !viewModel.sets.showOnlyPics
        viewModel.sets.showOnlyPics = newMode
        viewModel.updateSet()
        viewModel.updateDisplayList()

        if (newMode) Toast.makeText(requireContext(), "Only Posts with images", Toast.LENGTH_SHORT).show()
        else Toast.makeText(requireContext(), "Showing all Posts", Toast.LENGTH_SHORT).show()
    }

    /**
     * next image button
     * jumps and highlight to target
     */
    private fun btnNextButton() {
        if (viewModel.sets.listType == ThrdItemTyps.THREAD) {//single thread
            val newPos = viewModel.getNextPos(curViewedInd, scrollMode)
            if (newPos == -1) return
            scrollHighlight(newPos)
        } else { //board
            viewModel.navigatePage(+1, rel = true)
        }
    }

    /**
     * previous image button
     */
    private fun btnPrevButton() {
        if (viewModel.sets.listType == ThrdItemTyps.THREAD) {//single thread
            if (curViewedInd <= 0) return
            val layoutManag = (binding.postListRecView.layoutManager as LinearLayoutManager)
            val curView = layoutManag.findViewByPosition(curViewedInd) //null during scrolling, used to scroll to top of curview if in view

            val withinCur = layoutManag.findFirstVisibleItemPosition() == curViewedInd && curView != null && curView.top != 0

            val newPos = viewModel.getPrevPos(curViewedInd, scrollMode, withinCur)
            if (newPos == -1) return

            scrollHighlight(newPos)
        } else { //board
            viewModel.navigatePage(-1, rel = true)
        }
    }

    /**
     * first image button
     */
    private fun btnFirstButton() {
        if (viewModel.sets.listType == ThrdItemTyps.THREAD) {
            scrollHighlight(0)//quests must start with image anyway
        } else {
            viewModel.navigatePage(0, rel = false)
        }
    }

    /**
     * last image button
     */
    private fun btnLastButton() {
        if (viewModel.sets.listType == ThrdItemTyps.THREAD) {
            val newPos = viewModel.getLastPos(curViewedInd, scrollMode)
            if (newPos == -1) return
            scrollHighlight(newPos)
        } else {
            viewModel.navigatePage(viewModel.sets.curMaxPage, rel = false)
        }
    }

    fun backFromLink(navStat: Parcelable?) {
        binding.postListRecView.layoutManager?.onRestoreInstanceState(navStat)
    }

}
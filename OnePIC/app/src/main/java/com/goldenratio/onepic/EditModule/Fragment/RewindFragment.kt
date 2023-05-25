package com.goldenratio.onepic.EditModule.Fragment

import android.annotation.SuppressLint
import android.graphics.*
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.ImageView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.core.graphics.toRectF
import androidx.core.view.isEmpty
import androidx.core.view.setPadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.MutableLiveData
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.goldenratio.onepic.*
import com.goldenratio.onepic.EditModule.ArrowMoveClickListener
import com.goldenratio.onepic.EditModule.RewindModule
import com.goldenratio.onepic.PictureModule.Contents.ContentAttribute
import com.goldenratio.onepic.PictureModule.Contents.Picture
import com.goldenratio.onepic.PictureModule.ImageContent
import com.goldenratio.onepic.databinding.FragmentRewindBinding
import kotlinx.coroutines.*
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine


open class RewindFragment : Fragment(R.layout.fragment_rewind) {

    private lateinit var binding: FragmentRewindBinding

    protected lateinit var imageToolModule: ImageToolModule
    protected lateinit var rewindModule: RewindModule

    protected lateinit var mainPicture: Picture
    private lateinit var originalMainBitmap: Bitmap
    protected lateinit var mainBitmap: Bitmap

    private var preMainBitmap: Bitmap? = null
    protected var newImage: Bitmap? = null

    protected var changeFaceStartX = 0
    protected var changeFaceStartY = 0

    protected var bitmapList: ArrayList<Bitmap> = arrayListOf()

    protected val cropBitmapList: ArrayList<Bitmap> = arrayListOf()

    protected val jpegViewModel by activityViewModels<JpegViewModel>()
    protected lateinit var imageContent: ImageContent
    lateinit var fragment: Fragment

    private var infoLevel = MutableLiveData<InfoLevel>()

    protected var selectFaceRect: Rect? = null
    protected var isSelected = false

    private lateinit var mainSubView: View

    private enum class InfoLevel {
        EditFaceSelect,
        ChangeFaceSelect,
        ArrowCheck,
        BasicLevelEnd
    }

    private enum class LoadingText {
        FaceDetection,
        Save,
        Change,
        AutoRewind
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    @SuppressLint("ClickableViewAccessibility", "SetTextI18n")
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        bundle: Bundle?
    ): View {
        // 상태바 색상 변경
        val window: Window = activity?.window
            ?: throw IllegalStateException("Fragment is not attached to an activity")
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        window.setStatusBarColor(ContextCompat.getColor(requireContext(), android.R.color.black))

        // 뷰 바인딩 설정
        binding = FragmentRewindBinding.inflate(inflater, container, false)

        imageContent = jpegViewModel.jpegMCContainer.value?.imageContent!!

        imageToolModule = ImageToolModule()
        rewindModule = RewindModule()

//        imageToolModule.showView(binding.progressBar, true)
//        imageToolModule.showView(binding.loadingText, true)
        showProgressBar(true, LoadingText.FaceDetection)
        imageToolModule.showView(binding.rewindMenuLayout, false)

        // main Picture의 byteArray를 bitmap 제작
        mainPicture = imageContent.mainPicture

        // 메인 이미지 임시 설정
        CoroutineScope(Dispatchers.Default).launch {
            withContext(Dispatchers.Main) {
                Glide.with(binding.mainView)
                    .load(imageContent.getJpegBytes(imageContent.mainPicture))
                    .into(binding.mainView)
            }
        }

        CoroutineScope(Dispatchers.Default).launch {
            val newMainBitmap = imageContent.getMainBitmap()
            if (newMainBitmap != null) {
                mainBitmap = newMainBitmap
            }
            originalMainBitmap = mainBitmap
            // faceDetection 하고 결과가 표시된 사진을 받아 imaveView에 띄우기
            setMainImageBoundingBox()
        }
        CoroutineScope(Dispatchers.IO).launch {
            // rewind 가능한 연속 사진 속성의 picture list 얻음
            Log.d("faceRewind", "newBitmapList call before")
            val newBitmapList = imageContent.getBitmapList(ContentAttribute.edited)
            Log.d("faceRewind", "newBitmapList $newBitmapList")
            if (newBitmapList != null) {
                bitmapList = newBitmapList

                rewindModule.allFaceDetection(bitmapList)
            }
        }

        SetClickEvent()

        return binding.root
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        view.post {
            binding.circleArrowBtn.setOnTouchListener(ArrowMoveClickListener(::moveCropFace, binding.maxArrowBtn, binding.circleArrowBtn))
            imageToolModule.showView(binding.arrowBar, false)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    fun SetClickEvent() {
        // save btn 클릭 시
        binding.rewindSaveBtn.setOnClickListener {

            CoroutineScope(Dispatchers.Main).launch {
                imageToolModule.showView(binding.infoDialogLayout, false)
                infoLevel.value = InfoLevel.EditFaceSelect
            }

            CoroutineScope(Dispatchers.Default).launch {

//                imageToolModule.showView(binding.progressBar, true)
//                imageToolModule.showView(binding.loadingText, true)
                showProgressBar(true, LoadingText.Save)

                if (preMainBitmap != null) {
                    mainBitmap = preMainBitmap!!
                    newImage = null
                }

                val allBytes = imageToolModule.bitmapToByteArray(
                    mainBitmap,
                    imageContent.getJpegBytes(mainPicture)
                )

                imageContent.mainPicture =
                    Picture(ContentAttribute.edited, imageContent.extractSOI(allBytes))
                imageContent.mainPicture.waitForByteArrayInitialized()

                imageContent.setMainBitmap(mainBitmap)
                withContext(Dispatchers.Main) {
                    //jpegViewModel.jpegMCContainer.value?.save()
                    imageContent.checkRewindAttribute = true
                    findNavController().navigate(R.id.action_fregemnt_to_editFragment)
                }

//                imageToolModule.showView(binding.progressBar, false)
                showProgressBar(false, null)
            }
        }

        // close btn 클릭 시
        binding.rewindCloseBtn.setOnClickListener {
            try {
                findNavController().navigate(R.id.action_fregemnt_to_editFragment)
            } catch (e: IllegalStateException) {
                e.printStackTrace()
            }
        }

        // autoRewind 클릭시
        binding.autoRewindBtn.setOnClickListener {

            CoroutineScope(Dispatchers.Main).launch {
                imageToolModule.showView(binding.infoDialogLayout, false)
                infoLevel.value = InfoLevel.EditFaceSelect
            }

//            imageToolModule.showView(binding.progressBar, true)
            imageToolModule.showView(binding.arrowBar, false)
            imageToolModule.showView(binding.rewindMenuLayout, false)
            showProgressBar(true, LoadingText.AutoRewind)
            binding.candidateLayout.removeAllViews()

            if (bitmapList.size != 0) {
                CoroutineScope(Dispatchers.Default).launch {
                    rewindModule.allFaceDetection(bitmapList)
                    mainBitmap = rewindModule.autoBestFaceChange(bitmapList)

                    setMainImageBoundingBox()
                    newImage = null
//                    imageToolModule.showView(binding.progressBar, false)
                    imageToolModule.showView(binding.rewindMenuLayout, true)
                    showProgressBar(false, null)
                }
            }
            else {
                imageToolModule.showView(binding.rewindMenuLayout, true)
                showProgressBar(false, null)
            }
        }

        // compare 버튼 클릭시
        binding.imageCompareBtn.setOnTouchListener { _, event ->
            CoroutineScope(Dispatchers.Main).launch {
                imageToolModule.showView(binding.infoDialogLayout, false)
            }
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    binding.mainView.setImageBitmap(originalMainBitmap)
                    return@setOnTouchListener true
                }
                MotionEvent.ACTION_UP -> {
                    if (newImage != null)
                        binding.mainView.setImageBitmap(preMainBitmap)
                    else
                        binding.mainView.setImageBitmap(mainBitmap)
                    return@setOnTouchListener true
                }
                else -> return@setOnTouchListener false
            }
        }

        // reset 버튼 클릭시
        binding.imageResetBtn.setOnClickListener {
            CoroutineScope(Dispatchers.Main).launch {
                imageToolModule.showView(binding.infoDialogLayout, false)
            }
            binding.mainView.setImageBitmap(originalMainBitmap)
            mainBitmap = originalMainBitmap
            preMainBitmap = null
            newImage = null
        }

        // info 확인
        binding.rewindInfoBtn.setOnClickListener {
            imageToolModule.showView(binding.infoDialogLayout, true)
        }

        // info 삭제
        binding.dialogCloseBtn.setOnClickListener {
            imageToolModule.showView(binding.infoDialogLayout, false)
        }

        infoLevel.value = InfoLevel.EditFaceSelect
        infoLevel.observe(viewLifecycleOwner) { _ ->
            infoTextView()
        }

        // 이미지 뷰 클릭 시
        binding.mainView.setOnTouchListener { _, event ->
            if (event!!.action == MotionEvent.ACTION_UP) {
                if (!isSelected) {
//                imageToolModule.showView(binding.progressBar, true)
                    showProgressBar(true, LoadingText.Change)
                    imageToolModule.showView(binding.rewindMenuLayout, false)

//                    if (preMainBitmap != null) {
//                        mainBitmap = preMainBitmap!!
//                        newImage = null
//                    }
                    // click 좌표를 bitmap에 해당하는 좌표로 변환
                    val touchPoint = ImageToolModule().getBitmapClickPoint(
                        PointF(event.x, event.y),
                        binding.mainView
                    )
                    println("------- click point:$touchPoint")

                    if (touchPoint != null) {

                        CoroutineScope(Dispatchers.Default).launch {
                            // Click 좌표가 포함된 Bounding Box 얻음
                            while (!rewindModule.getCheckFaceDetection()) {
                            }
                            val boundingBox = getBoundingBox(touchPoint)

                            if (boundingBox.size > 0) {
                                // Bounding Box로 이미지를 Crop한 후 보여줌
                                withContext(Dispatchers.Main) {
                                    cropImgAndView(boundingBox)
                                }
                            }
                        }
                    } else {
//                    imageToolModule.showView(binding.progressBar, false)
                        showProgressBar(false, null)
                    }
                }
            }
            return@setOnTouchListener true
        }

        binding.faceSaveBtn.setOnClickListener {
            isSelected = false

            if (preMainBitmap != null) {
                mainBitmap = preMainBitmap!!
                newImage = null
                preMainBitmap = null
            }

            binding.mainView.setImageBitmap(mainBitmap)
            imageToolModule.showView(binding.faceRewindMenuLayout, false)
            setMainImageBoundingBox()
        }

        binding.faceCancleBtn.setOnClickListener {
            isSelected = false

            newImage = null
            preMainBitmap = null

            binding.mainView.setImageBitmap(mainBitmap)
            imageToolModule.showView(binding.faceRewindMenuLayout, false)
            setMainImageBoundingBox()
        }
    }

    /**
     * setMainImageBoundingBox()
     *      - mainImage를 faceDetection 실행 후,
     *        감지된 얼굴의 사각형 표시된 사진으로 imageView 변환
     */
    open fun setMainImageBoundingBox() {

        if(infoLevel.value != InfoLevel.EditFaceSelect) {
            CoroutineScope(Dispatchers.Main).launch {
                infoLevel.value = InfoLevel.EditFaceSelect
            }
        }

        //showView(binding.faceListView, false)
        imageToolModule.showView(binding.arrowBar, false)
        if (!binding.candidateLayout.isEmpty()) {
            CoroutineScope(Dispatchers.Main).launch {
                withContext(Dispatchers.Main) {
                    binding.candidateLayout.removeAllViews()
                }
            }
        }

        CoroutineScope(Dispatchers.Default).launch {
            Log.d("checkPictureList", "!!!!!!!!!!!!!!!!!!! setMainImageBoundingBox")
            val faceResult = rewindModule.runFaceDetection(0)
//            val faceResult = rewindModule.runMainFaceDetection(mainBitmap)
            Log.d("checkPictureList", "!!!!!!!!!!!!!!!!!!! end runFaceDetection")

            if (faceResult.size == 0) {
                withContext(Dispatchers.Main) {
                    try {
                    Toast.makeText(requireContext(), "사진에 얼굴이 존재하지 않습니다.", Toast.LENGTH_SHORT)
                        .show()
//                        imageToolModule.showView(binding.progressBar, false)
                        showProgressBar(false, null)
                        imageToolModule.showView(binding.rewindMenuLayout, true)
                    } catch (e: IllegalStateException) {
                        println(e.message)
                    }
                }
            } else {
                try {
                    val resultBitmap =
                        imageToolModule.drawDetectionResult(mainBitmap, faceResult, requireContext().resources.getColor(R.color.white))

                    // imageView 변환
                    withContext(Dispatchers.Main) {
                        binding.mainView.setImageBitmap(resultBitmap)
                    }
                } catch (e: IllegalStateException) {
                    println(e.message)
                }
            }
//            imageToolModule.showView(binding.progressBar, false)
            showProgressBar(false, null)
            imageToolModule.showView(binding.rewindMenuLayout, true)

        }
    }

    /**
     * getBoundingBox(touchPoint: Point): ArrayList<List<Int>>
     *     - click된 포인트를 알려주면,
     *       해당 포인트가 객체 감지 결과 bounding Box 속에 존재하는지 찾아서
     *       만약 포인트를 포함하는 boundingBox를 찾으면 모아 return
     */
    suspend fun getBoundingBox(touchPoint: Point): ArrayList<ArrayList<Int>> = suspendCoroutine { box ->
        val boundingBox: ArrayList<ArrayList<Int>> = arrayListOf()

        val checkFinish = BooleanArray(bitmapList.size)
        for (i in 0 until bitmapList.size) {
            checkFinish[i] = false
//            boundingBox.add(arrayListOf(0,0,0,0,0,0,0,0,0))
        }

        CoroutineScope(Dispatchers.IO).launch {
            if (bitmapList.size == 0) {
//                imageToolModule.showView(binding.progressBar, false)
                showProgressBar(false, null)
                return@launch
            }

            val basicRect =
                rewindModule.getClickPointBoundingBox(bitmapList[0], 0, touchPoint)

            if (basicRect == null) {
                withContext(Dispatchers.Main) {
                    try {
                        Toast.makeText(
                            requireContext(),
                            "해당 좌표에 얼굴이 존재 하지 않습니다.",
                            Toast.LENGTH_LONG
                        )
                            .show()
                    } catch (e: IllegalStateException) {
                        println(e.message)
                    }
                }
                // 메인 사진의 boundingBox에 인지된 얼굴이 없을 때
                // faceDetection하고 결과가 표시된 사진을 받아 imaveView에 띄우기
                setMainImageBoundingBox()

                checkFinish.fill(true) // 배열의 모든 요소를 true로 설정
            } else {

                selectFaceRect = Rect(basicRect[0], basicRect[1], basicRect[2], basicRect[3])

                // 메인 사진일 경우 나중에 다른 사진을 겹칠 위치 지정
                changeFaceStartX = basicRect[4]
                changeFaceStartY = basicRect[5]

                val arrayBounding = arrayListOf(
                    0,
                    basicRect[0], basicRect[1], basicRect[2], basicRect[3],
                    basicRect[4], basicRect[5], basicRect[6], basicRect[7]
                )
                boundingBox.add(arrayBounding)
                checkFinish[0] = true
                for (i in 1 until bitmapList.size) {
//                    CoroutineScope(Dispatchers.Default).launch {
                        // clickPoint와 사진을 비교하여 클릭된 좌표에 감지된 얼굴이 있는지 확인 후 해당 얼굴 boundingBox 받기
                        val rect =
                            rewindModule.getClickPointBoundingBox(bitmapList[i], i, touchPoint)

                        if (rect != null) {
                            val arrayBounding = arrayListOf(
                                i,
                                rect[0], rect[1], rect[2], rect[3],
                                rect[4], rect[5], rect[6], rect[7]
                            )
                            boundingBox.add(arrayBounding)
                        }
                        checkFinish[i] = true
                    }
//                }
            }
            while (!checkFinish.all { it }) {
            }
            box.resume(boundingBox)
        }
    }


    /**
     *  cropImgAndView(boundingBox: ArrayList<List<Int>>)
     *         - 이미지를 자르고 화면에 띄어줌
     */
    private fun cropImgAndView(boundingBox: ArrayList<ArrayList<Int>>) {

        imageToolModule.showView(binding.faceRewindMenuLayout, true)
        isSelected = true
        changeMainView()

        // 감지된 모든 boundingBox 출력
        println("=======================================================")
        binding.candidateLayout.removeAllViews()
        cropBitmapList.clear()

        if (bitmapList.size == 0) {
//            imageToolModule.showView(binding.progressBar , false)
            showProgressBar(false, null)
            return
        }

        for (i in 0 until boundingBox.size) {
            println(i.toString() + " || " + boundingBox[i])

            // bounding rect 알아내기
            val rect = boundingBox[i]

            // bitmap를 자르기
            val cropImage = imageToolModule.cropBitmap(
                bitmapList[rect[0]],
                //bitmapList[rect[0]].copy(Bitmap.Config.ARGB_8888, true),
                Rect(rect[1], rect[2], rect[3], rect[4])
            )

            try {
                // 넣고자 하는 layout 불러오기
                val candidateLayout = layoutInflater.inflate(R.layout.candidate_image_array, null)

                // 위 불러온 layout에서 변경을 할 view가져오기
                val cropImageView: ImageView =
                    candidateLayout.findViewById(R.id.cropImageView)

                // 자른 사진 이미지뷰에 붙이기
                cropImageView.setImageBitmap(cropImage)
                // crop 된 후보 이미지 클릭시 해당 이미지로 얼굴 변환 (rewind)
                cropImageView.setOnClickListener {

                    cropImageView.setBackgroundResource(R.drawable.chosen_image_border)
                    cropImageView.setPadding(imageToolModule.floatToDp(requireContext(),3.0f).toInt())

                    newImage = imageToolModule.cropBitmap(
                        bitmapList[rect[0]],
                        //bitmapList[rect[0]].copy(Bitmap.Config.ARGB_8888, true),
                        Rect(rect[5], rect[6], rect[7], rect[8])
                    )
                    newImage = imageToolModule.circleCropBitmap(newImage!!)

                    // 크롭이미지 배열에 값 추가
                    cropBitmapList.add(newImage!!)

                    preMainBitmap = imageToolModule.overlayBitmap(
                        mainBitmap,
                        newImage!!,
                        changeFaceStartX,
                        changeFaceStartY
                    )

                    binding.mainView.setImageBitmap(preMainBitmap)
                    imageToolModule.showView(binding.arrowBar, true)
                    infoLevel.value = InfoLevel.ArrowCheck
                }

                // main activity에 만들어둔 scrollbar 속 layout의 아이디를 통해 해당 layout에 넣기
                binding.candidateLayout.addView(candidateLayout)
            } catch (e: IllegalStateException) {
                println(e.message)
            }
        }
//        imageToolModule.showView(binding.progressBar , false)
        showProgressBar(false, null)
        imageToolModule.showView(binding.arrowBar, false)
        infoLevel.value = InfoLevel.ChangeFaceSelect
    }

    private fun moveCropFace(moveX:Int, moveY:Int) {
        if(infoLevel.value != InfoLevel.BasicLevelEnd)
            infoLevel.value = InfoLevel.BasicLevelEnd
        if (newImage != null) {

            changeFaceStartX += moveX
            changeFaceStartY += moveY

            println("!!!!!!!!!! change point (${changeFaceStartX}, ${changeFaceStartY})")
            if(changeFaceStartX < 0)
                changeFaceStartX = 0
            else if(changeFaceStartX > mainBitmap.width- newImage!!.width)
                changeFaceStartX = mainBitmap.width - newImage!!.width
            if(changeFaceStartY < 0)
                changeFaceStartY = 0
            else if(changeFaceStartY > mainBitmap.height - newImage!!.height)
                changeFaceStartY = mainBitmap.height - newImage!!.height

            println("==== change point (${changeFaceStartX}, ${changeFaceStartY})")
            preMainBitmap = imageToolModule.overlayBitmap(
                mainBitmap,
                newImage!!,
                changeFaceStartX,
                changeFaceStartY
            )

            binding.mainView.setImageBitmap(preMainBitmap)
        }
    }

    open fun infoTextView() {
        Log.d("infoTextView","infoTextView call")
        when (infoLevel.value) {
            InfoLevel.EditFaceSelect -> {
                binding.infoText.text = "변경을 원하는 얼굴을 누릅니다."
            }
            InfoLevel.ChangeFaceSelect -> {
                binding.infoText.text = "아래 사진을 보고\n마음에 드는 얼굴을 선택합니다."
            }
            InfoLevel.ArrowCheck -> {
                binding.infoText.text = "변경된 얼굴의 위치는\n조정 바를 통해 수정할 수 있습니다."
            }
            InfoLevel.BasicLevelEnd -> {
                binding.infoText.text = "자동 얼굴 변경 버튼을 누르면 자동으로\n 모든 사람이 잘나온 얼굴로 변경됩니다."
            }
            else -> {}
        }
    }

    open fun changeMainView() {
        if(selectFaceRect != null) {
            val newBitmap = imageToolModule.drawDetectionResult(mainBitmap, selectFaceRect!!.toRectF(), requireContext().resources.getColor(R.color.select_face))
            binding.mainView.setImageBitmap(newBitmap)
        }
    }

    private fun showProgressBar(boolean: Boolean, loadingText: LoadingText?){
        if(boolean) {
            imageToolModule.showView(binding.infoDialogLayout, false)
        }
        else {
            imageToolModule.showView(binding.infoDialogLayout, true)
        }

        CoroutineScope(Dispatchers.Main).launch {
            binding.loadingText.text = when (loadingText) {
                LoadingText.FaceDetection -> {
                    "얼굴을 분석 중..."
                }
                LoadingText.Save -> {
                    "편집을 저장 중.."
                }
                LoadingText.AutoRewind -> {
                    "얼굴을 추천 중입니다."
                }
                else -> {
                    ""
                }
            }
        }

        imageToolModule.showView(binding.progressBar, boolean)
        imageToolModule.showView(binding.loadingText, boolean)
    }


    override fun onDestroy() {
        super.onDestroy()
        rewindModule.deleteModelCoroutine()
    }
    override fun onStop() {
        super.onStop()
        rewindModule.deleteModelCoroutine()
    }
}

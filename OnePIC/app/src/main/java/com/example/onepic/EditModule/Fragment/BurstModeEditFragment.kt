package com.example.onepic.EditModule.Fragment

import android.graphics.Bitmap
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.bumptech.glide.Glide
import com.example.onepic.JpegViewModel
import com.example.onepic.PictureModule.ImageContent
import com.example.onepic.R
import com.example.onepic.databinding.FragmentBurstModeEditBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BurstModeEditFragment : Fragment() {


    private lateinit var binding: FragmentBurstModeEditBinding
    lateinit var fragment: Fragment
    private val jpegViewModel by activityViewModels<JpegViewModel>()
    private lateinit var imageContent : ImageContent

    private lateinit var mainBitmap: Bitmap
    private var bitmapList: ArrayList<Bitmap> = arrayListOf()


    private var isAdd : Boolean = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // 뷰 바인딩 설정
        binding = FragmentBurstModeEditBinding.inflate(inflater, container, false)

        imageContent = jpegViewModel.jpegMCContainer.value?.imageContent!!

        CoroutineScope(Dispatchers.Default).launch {
            // 파일을 parsing해서 PictureContainer로 바꾸는 함수 호출
            // 메인 이미지 설정
            withContext(Dispatchers.Main) {
                Glide.with(binding.burstMainView)
                    .load(imageContent.getJpegBytes(imageContent.mainPicture))
                    .into(binding.burstMainView)
            }
        }
        CoroutineScope(Dispatchers.Default).launch{
            mainBitmap = imageContent.getMainBitmap()
        }
        CoroutineScope(Dispatchers.Default).launch{
            bitmapList = imageContent.getBitmapList()

            for(i in 0 until bitmapList.size) {
                // 넣고자 하는 layout 불러오기
                val candidateLayout = layoutInflater.inflate(R.layout.candidate_image_array, null)

                // 위 불러온 layout에서 변경을 할 view가져오기
                val cropImageView: ImageView =
                    candidateLayout.findViewById(R.id.cropImageView)

                // 자른 사진 이미지뷰에 붙이기
                cropImageView.setImageBitmap(bitmapList[i])

                // main activity에 만들어둔 scrollbar 속 layout의 아이디를 통해 해당 layout에 넣기
                binding.candidateLayout.addView(candidateLayout)
            }
        }
        return binding.root
    }

}
package com.example.camerax

import android.Manifest
import android.app.AlertDialog
import android.app.Dialog
import android.content.ContentValues
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import com.example.camerax.databinding.ActivityMainBinding
import java.io.File
import java.io.FileOutputStream
import java.net.URI
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

typealias LumaListener = (luma: Double) -> Unit
//typealias : 긴 제네릭 타입을 사용하는 변수들에 대한 새로운 별명을 지어주고, 짧게 사용할 수 있다.
// (luma: Double) -> unit : 고차함수랑 함수의 인자나 반환값이 lambda인 경우를 말하며
// list의 filter나 map은 인자로 람다를 받는다 고로 luma는 인자 double은 타입 -> 반환타입인데 Unit임으로 반환값이 없음을 나타낸다

class MainActivity : AppCompatActivity() {
    private lateinit var viewBinding: ActivityMainBinding

// var 변수명 : 타입 = 값

    private var imageCapture: ImageCapture? = null

    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null

    private lateinit var cameraExecutor: ExecutorService

    var dialog01 : Dialog?=null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)

        // Request camera permissions
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            // ActivityCompat에서 이앱에 부여할 권한을 요청하는 메소드(activity, 권한, 요청구분코드)
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }

        DialogCss("")

        viewBinding.imageCaptureButton.setVisibility(View.GONE)
        viewBinding.saveButton.setVisibility(View.GONE)
        viewBinding.retakeButton.setVisibility(View.GONE)
        viewBinding.nextButton.setVisibility(View.GONE)
        viewBinding.cameraImageView.setVisibility(View.GONE)

        viewBinding.imageCaptureButton.setOnClickListener { takePhotoCapture() }

//        viewBinding.saveButton.setOnClickListener { takePhoto() }
//        viewBinding.videoCaptureButton.setOnClickListener { captureVideo() }

        cameraExecutor = Executors.newSingleThreadExecutor()
    }

    private fun guideOne() {

        viewBinding.imageCaptureButton.setVisibility(View.VISIBLE)
    }

    // REQUIRED_PERMISSIONS.all : REQUIRED_PERMISSIONS의 들어간 권한 전부를 뜻함 array타입
    // ContextCompat.checkSelfPermission : ContextCompat의 특정권한이 부여되었는지 확인하는메서드( context, permission)
    // 권한이 확인되면 PackageManager.PERMISSION_GRANTED반환, 권한이 확인되지않으면 PERMISSION_DENIED 반환
    // baseContext : Context가 생성할때 만들어지는 녀석들의 기본 Context
    // ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED일경우 true 반환 > 위에 if문과 연결됨
    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
    // companion object는 클래스 같은 객체이다.
    companion object {
        private const val TAG = "CameraXApp"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS =
            mutableListOf (
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO
            ).apply {
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                    add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }.toTypedArray()
        // toTypedArray() : 위 list를 array로 변환한다.
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults:
        IntArray) {
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(this,
                    "Permissions not granted by the user.",
                    Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun startCamera() {
        // ProcessCameraProvider의 수명주기를 바인딩하는것이다. camerax는 수명주기를 인식하므로 별도의 카메라를 열고닫는작업이필요하지않다.
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        //addListener 계속 add하는것
        cameraProviderFuture.addListener({
            // Used to bind the lifecycle of cameras to the lifecycle owner
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // Preview
            // .build()는 preview상태지정, also는 it 객체로 접근하여 수신객체를 람다의 파라미터로 전달
            // it=preview이다 -> it.setSurfaceProvider(viewBinding.viewFinder.surfaceProvider)
            // 미리보기를 제공하도록 설정하는것 호출될위치 -> viewBinding : this(이 액티비티),
            // viewFinder : androidx.camera.view.PreviewView이름
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(viewBinding.viewFinder.surfaceProvider)
                }
// Quality.HIGHEST : 최고 비디오품질 설정
//            val recorder = Recorder.Builder()
//                .setQualitySelector(QualitySelector.from(Quality.HIGHEST))
//                .build()
//            videoCapture = VideoCapture.withOutput(recorder)

            // 그냥 올리는거랑 똑같은데 촬영 시작후에 올라감
//            viewBinding.viewFinder.overlay.add(viewBinding.cameraImageView)

            //startCamera() 사진미리보기를 위한 코드
            imageCapture = ImageCapture.Builder()
                .build()

            // val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            // :기본 후면 카메라를 선택하는것
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                // cameraProvider에 아무것도 바인딩되지않았는지 확인
                // cameraProvider : 카레마 존재 또는 정보쿼리와 같은 카메라세트에 대한 기본 액세스제공
                cameraProvider.unbindAll()

                // cameraSelector 및 preview(미리보기개체)를 cameraProvider넣는다
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture)
//                cameraProvider
//                    .bindToLifecycle(this, cameraSelector, preview, videoCapture)

            } catch(exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    //캡쳐한 사진을 임시파일에 저장
    private fun takePhotoCapture() {
        // val imageCapture = imageCapture ?: return : imageCapture가 null일경우 함수를 종료하고
        // 아니면 리턴
        val imageCapture = imageCapture ?: return



        // Locale.US : 미국일경우 일요일을 sun이라고 하기때문에 sun으로 표시
        // currentTimeMillis : 현재시간을 구한다.

        val name = SimpleDateFormat(FILENAME_FORMAT, Locale.US)
            .format(System.currentTimeMillis())

        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            //put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
//            if(Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
//                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/CameraX-Image")
//            }
        }

        // 사진을 지정할수 있는곳이다.
        //contentResolver : MediaStore에 액세스하게 해주는것
        // MediaStore.Images.Media.EXTERNAL_CONTENT_URI : MediaStore에 출력을 저장하기위해 경로입력

        //.Builder(File(this.cacheDir?.absolutePath + "/" + contentValues)) : 캐시경로설정
        val outputOptions = ImageCapture.OutputFileOptions
            .Builder(File(this.cacheDir?.absolutePath + "/" + contentValues))
            .build()

        //ImageCapture.OnImageSavedCallback : 이미지 캡쳐.이미지저장콜백으로
        // onError : 저장을 시도하는 동안 오류가 발생하면 호출
        // onImageSaved : 이미지가 성공적으로 저장되면 호출
        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed: ${exc.message}", exc)

                }

                override fun
                        onImageSaved(output: ImageCapture.OutputFileResults){
                    val msg = "캐시저장: ${output.savedUri}"
                    Toast.makeText(this@MainActivity, msg, Toast.LENGTH_SHORT).show()
//                    var imageUri:Uri?=contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,contentValues)
                    // 찍은사진을 바로 화면에 imageviewC로 보여주기
                    viewBinding.cameraImageViewC.setImageURI(output.savedUri)
                    viewBinding.saveButton.setVisibility(View.VISIBLE)
                    Log.d(TAG, msg)
                    viewBinding.retakeButton.setVisibility(View.VISIBLE)
                    viewBinding.imageCaptureButton.setVisibility(View.GONE)
                    viewBinding.retakeButton.setOnClickListener{
                        startCamera()
                        viewBinding.retakeButton.setVisibility(View.GONE)
                        viewBinding.saveButton.setVisibility(View.GONE)
                        viewBinding.imageCaptureButton.setVisibility(View.VISIBLE)
                    }

                    viewBinding.saveButton.setOnClickListener {
                    val bitmap = (viewBinding.cameraImageViewC.drawable as BitmapDrawable).bitmap

                    val rootPath =
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                            .toString()
                    val dirName = "/" + "Pictures/CameraX-Image"
                    val fileName = System.currentTimeMillis().toString() + ".png"
                    val savePath = File(rootPath + dirName)
                    savePath.mkdirs()

                    val file = File(savePath, fileName)

                    val out = FileOutputStream(file)
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                    out.flush()
                    out.close()

                    this@MainActivity.sendBroadcast(
                        Intent(
                            Intent.ACTION_MEDIA_SCANNER_SCAN_FILE,
                            Uri.parse("file://" + Environment.getExternalStorageDirectory())
                        )
                    )
                        Toast.makeText(this@MainActivity, "사진저장됐습니다.", Toast.LENGTH_SHORT).show()

                    }
                }
            }
        )
    }

    //save버튼눌렀을시 이동
    private fun takePhoto() {
        // val imageCapture = imageCapture ?: return : imageCapture가 null일경우 함수를 종료하고
        // 아니면 리턴
        val imageCapture = imageCapture ?: return

        // Locale.US : 미국일경우 일요일을 sun이라고 하기때문에 sun으로 표시
        // currentTimeMillis : 현재시간을 구한다.
        val name = SimpleDateFormat(FILENAME_FORMAT, Locale.US)
            .format(System.currentTimeMillis())
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if(Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/CameraX-Image")
            }
        }

        // 사진을 지정할수 있는곳이다.
        //contentResolver : MediaStore에 액세스하게 해주는것
        // MediaStore.Images.Media.EXTERNAL_CONTENT_URI : MediaStore에 출력을 저장하기위해 경로입력
        val outputOptions = ImageCapture.OutputFileOptions
            .Builder(contentResolver,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues)
            .build()

        //ImageCapture.OnImageSavedCallback : 이미지 캡쳐.이미지저장콜백으로 
        // onError : 저장을 시도하는 동안 오류가 발생하면 호출
        // onImageSaved : 이미지가 성공적으로 저장되면 호출
        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed: ${exc.message}", exc)

                }

                override fun
                        onImageSaved(output: ImageCapture.OutputFileResults){
                    val msg = "사진저장완료: ${output.savedUri}"
                    viewBinding.cameraImageViewC.setVisibility(View.VISIBLE)
                    viewBinding.cameraImageViewC.setImageURI(output.savedUri)
                    viewBinding.saveButton.setVisibility(View.VISIBLE)
                    Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT).show()
                    Log.d(TAG, msg)
                    viewBinding.nextButton.setVisibility(View.VISIBLE)
                    viewBinding.nextButton.setOnClickListener {

                        viewBinding.imageCaptureButton.setVisibility(View.GONE)
                        viewBinding.saveButton.setVisibility(View.GONE)
                        viewBinding.retakeButton.setVisibility(View.GONE)
                        viewBinding.nextButton.setVisibility(View.GONE)

                        DialogCss("with Creadit card")

                        viewBinding.cameraImageViewC.setVisibility(View.GONE)
                        viewBinding.cameraImageView.setVisibility(View.VISIBLE)
                        startCamera()
                    }
                }
            }
        )
    }

    private fun DialogCss(msg : String) {

        var dialog01 : Dialog?=null
        dialog01 = Dialog(this)
        dialog01.setContentView(R.layout.dialog_main)

        var Okbutton : Button
        var bottom : TextView

        dialog01.window!!.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        Okbutton = dialog01.findViewById(R.id.Ok_button)
        bottom  = dialog01.findViewById(R.id.bottom)

        bottom.setText(msg)

        dialog01.show()

        Okbutton.setOnClickListener {
            guideOne()
            dialog01.dismiss()
        }
    }


//    private fun captureVideo() {
//        val videoCapture = this.videoCapture ?: return
//
//        //동영상을 찍는 중에는 버튼UI가 비활성화 된다. 이후에도 다시 나타나게 할예정
//        viewBinding.videoCaptureButton.isEnabled = false
//        val curRecording = recording
//        //동영상을 찍는중일 경우 stop()을 이용해서 멈출수있다.
//        if (curRecording != null) {
//            // Stop the current recording session.
//            curRecording.stop()
//            recording = null
//            return
//        }
//
//        // 동영상 저장하는것
//        val name = SimpleDateFormat(FILENAME_FORMAT, Locale.US)
//            .format(System.currentTimeMillis())
//        val contentValues = ContentValues().apply {
//            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
//            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
//            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
//                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/CameraX-Video")
//            }
//        }
//        // video출력을 mediastore에 저장하기위해서 경로 설정
//        // video의 경우 사진과 달리 setContentValues(contentValues) 방식을 써야함
//        val mediaStoreOutputOptions = MediaStoreOutputOptions
//            .Builder(contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
//            .setContentValues(contentValues)
//            .build()
//        // .prepareRecording(this, mediaStoreOutputOptions) : 저장할비디오를 준비한다
//        recording = videoCapture.output
//            .prepareRecording(this, mediaStoreOutputOptions)
//            .apply {
//                if (PermissionChecker.checkSelfPermission(this@MainActivity,
//                        Manifest.permission.RECORD_AUDIO) ==
//                    PermissionChecker.PERMISSION_GRANTED)
//                {
//                    //오디오 녹음하는 메소드
//                    withAudioEnabled()
//                }
//            }
//            .start(ContextCompat.getMainExecutor(this)) { recordEvent ->
//                when(recordEvent) {
//                    is VideoRecordEvent.Start -> {
//                        viewBinding.videoCaptureButton.apply {
//                            text = getString(R.string.stop_capture)
//                            isEnabled = true
//                        }
//                    }
//                    is VideoRecordEvent.Finalize -> {
//                        if (!recordEvent.hasError()) {
//                            val msg = "Video capture succeeded: " +
//                                    "${recordEvent.outputResults.outputUri}"
//                            Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT)
//                                .show()
//                            Log.d(TAG, msg)
//                        } else {
//                            recording?.close()
//                            recording = null
//                            Log.e(TAG, "Video capture ends with error: " +
//                                    "${recordEvent.error}")
//                        }
//                        viewBinding.videoCaptureButton.apply {
//                            text = getString(R.string.start_capture)
//                            isEnabled = true
//                        }
//                    }
//                }
//            }
//    }
}
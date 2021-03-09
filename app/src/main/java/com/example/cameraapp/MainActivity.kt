package com.example.cameraapp

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentResolver
import android.content.pm.PackageManager
import android.graphics.*
import android.media.Image
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.core.ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceContour
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.android.synthetic.main.activity_main.*
import org.opencv.android.BaseLoaderCallback
import org.opencv.android.LoaderCallbackInterface
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors


typealias faceListener = (face: Double) -> Unit

class MainActivity : AppCompatActivity() {
    private var imageCapture: ImageCapture? = null

    override fun getContentResolver(): ContentResolver {
        return super.getContentResolver()
    }


    private lateinit var outputDirectory: File
    private lateinit var cameraExecutor: ExecutorService

    val currentImageType = Imgproc.COLOR_RGB2GRAY


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Request camera permissions
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                    this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }

        // Set up the listener for take photo button
        camera_capture_button.setOnClickListener { takePhoto() }

        outputDirectory = getOutputDirectory()

        cameraExecutor = Executors.newSingleThreadExecutor()
    }


    // has to be removed in the future
    private fun takePhoto(){
         fun getContentResolver(): ContentResolver {
            return super.getContentResolver()
        }

        // Get a stable reference of the modifiable image capture use case
        val imageCapture = imageCapture ?: return

        // Create time-stamped output file to hold the image
        val photoFile = File(
                outputDirectory,
                SimpleDateFormat(FILENAME_FORMAT, Locale.US
                ).format(System.currentTimeMillis()) + ".png")

        // Create output options object which contains file + metadata
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        // Set up image capture listener, which is triggered after photo has
        // been taken
        imageCapture.takePicture(
                outputOptions, ContextCompat.getMainExecutor(this), object : ImageCapture.OnImageSavedCallback {
            override fun onError(exc: ImageCaptureException) {
                Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
            }

            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                val savedUri = Uri.fromFile(photoFile)
                val msg = "Photo capture succeeded: $savedUri"
                Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT).show()
                Log.d(TAG, msg)
            }
        })
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener(Runnable {
            // Used to bind the lifecycle of cameras to the lifecycle owner
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // Preview
            val preview = Preview.Builder()
                    .build()
                    .also {
                        it.setSurfaceProvider(viewFinder.createSurfaceProvider())
                    }

            imageCapture = ImageCapture.Builder()
                    .build()

            val imageAnalyzer = ImageAnalysis.Builder()
                    .setBackpressureStrategy(STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                    .also {
                        it.setAnalyzer(cameraExecutor, FaceAnalyzer { face ->
                            Log.d(TAG, "Average face: $face")
                            contentResolver
                        })
                    }

            // Select front camera as a default
            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

            try {
                // Unbind use cases before rebinding
                cameraProvider.unbindAll()

                // Bind use cases to camera
                cameraProvider.bindToLifecycle(
                        this, cameraSelector,preview , imageCapture, imageAnalyzer)

            } catch(exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
                baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun getOutputDirectory(): File {
        val mediaDir = externalMediaDirs.firstOrNull()?.let {
            File(it, resources.getString(R.string.app_name)).apply { mkdirs() }
        }
        return if (mediaDir != null && mediaDir.exists())
            mediaDir else filesDir
    }

    private val mLoaderCallback: BaseLoaderCallback = object : BaseLoaderCallback(this) {
        override fun onManagerConnected(status: Int) {
            when (status) {
                LoaderCallbackInterface.SUCCESS -> {
                    Log.i("OpenCV", "OpenCV loaded successfully")
                }
                else -> {
                    super.onManagerConnected(status)
                }
            }
        }
    }


    override fun onResume() {
        super.onResume()
            if (!OpenCVLoader.initDebug()) {
                Log.d("OpenCV", "Internal OpenCV library not found. Using OpenCV Manager for initialization");
                OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_0_0, this, mLoaderCallback);
            } else {
                Log.d("OpenCV", "OpenCV library found inside package. Using it!");
                mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
            }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()

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

    companion object {
        private const val TAG = "CameraXBasic"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)

        var sd = Environment.getExternalStorageDirectory()

        var dummy1 : PointF? = null
        var dummy2 : PointF? = null
        var dummy3 : PointF? = null
        var dummy4 : PointF? = null



    }

    //             [ PREPARE INPUT IMAGE ]             //
    private class FaceAnalyzer(private val listener: faceListener) : ImageAnalysis.Analyzer {

        private fun ByteBuffer.toByteArray(): ByteArray {
            rewind()    // Rewind the buffer to zero
            val data = ByteArray(remaining())
            get(data)   // Copy the buffer into a byte array
            return data // Return the byte array
        }

        fun Image.toBitmap(): Bitmap {
            val yBuffer = planes[0].buffer // Y
            val vuBuffer = planes[2].buffer // VU

            val ySize = yBuffer.remaining()
            val vuSize = vuBuffer.remaining()

            val nv21 = ByteArray(ySize + vuSize)

            yBuffer.get(nv21, 0, ySize)
            vuBuffer.get(nv21, ySize, vuSize)

            val yuvImage = YuvImage(nv21, ImageFormat.NV21, this.width, this.height, null)
            val out = ByteArrayOutputStream()
            yuvImage.compressToJpeg(Rect(0, 0, yuvImage.width, yuvImage.height), 50, out)
            val imageBytes = out.toByteArray()
            return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
        }


        @RequiresApi(Build.VERSION_CODES.O)
        @SuppressLint("UnsafeExperimentalUsageError", "NewApi")
        override fun analyze(imageProxy : ImageProxy) {

            val buffer = imageProxy.planes[0].buffer
            val data = buffer.toByteArray()
            val pixels = data.map { it.toInt() and 0xFF }
            val pix = pixels.average()
//            val red = Color.red(pix.toInt())
//            val average = pixels.average()
//            Log.d(TAG , "Red : $red")

            val mediaImage = imageProxy.image
            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
//            val bmp  = InputImage.fromByteArray(data,480,360,imageProxy.imageInfo.rotationDegrees,InputImage.IMAGE_FORMAT_NV21)
12


//            val n_rows: Int = 480
//            val n_cols: Int = 360

            val bmp : Bitmap? = image.mediaImage?.toBitmap()

            val bmp2 = bmp?.height?.let { createBitmap(bmp?.width , it, Bitmap.Config.ARGB_8888) }

            ///////////////////////////////////////////////////////////////////

            val mat : Mat = Mat.zeros(480, 360 ,CvType.CV_8UC3)
            val mat2 : Mat = Mat.zeros(480 , 360 ,CvType.CV_8UC1)
            Utils.bitmapToMat(bmp , mat)
            Imgproc.cvtColor(mat , mat2 , Imgproc.COLOR_RGB2GRAY)

            Utils.matToBitmap(mat2 , bmp2 )


            ////////////////////////////////////////////////////////////////////
/*
            val mat : Mat = Mat.zeros(480   , 640 ,CvType.CV_8UC3)
            val greymat : Mat = Mat.zeros(480   , 640 ,CvType.CV_8UC1)
            val cny  = Mat.zeros(480 , 640 , CvType.CV_8UC3)

            Utils.bitmapToMat(bmp , mat)

            Imgproc.cvtColor(mat , greymat , Imgproc.COLOR_RGB2GRAY)
            Imgproc.Canny(greymat, cny, 10.0, 100.0, 3, true)

            Utils.matToBitmap(cny , bmp)
*/
            val col = bmp2?.getPixel(0,0)?.toInt()
            Log.d(TAG , " colorspace : $col")

           ////////////////////////////////////////////////////////////////////

            val bmpInput = InputImage.fromBitmap( bmp2, imageProxy.imageInfo.rotationDegrees )

        ////////////////////////////////////////////////////////////////////

            // [START set_detector_options]
                val realTimeOpts = FaceDetectorOptions.Builder()

                        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                        .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
//                      .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
//                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
//                .setMinFaceSize(0.15f)
//                .enableTracking()
                        .build()
                // [END set_detector_options]

                // [START get_detector]
                val detector: com.google.mlkit.vision.face.FaceDetector = FaceDetection.getClient(realTimeOpts)
                // Or, to use the default option:
                // val detector = FaceDetection.getClient();
                // [END get_detector]


                // [START run_detector]
                val result = detector.process(bmpInput)
                        .addOnSuccessListener { faces ->
                            // Task completed successfully
                            // [START_EXCLUDE]
                            // [START get_face_info]
                            for (face in faces) {
                                val bounds = face.boundingBox
                                val rotY = face.headEulerAngleY // Head is rotated to the right rotY degrees
                                val rotZ = face.headEulerAngleZ // Head is tilted sideways rotZ degrees

                                // If landmark detection was enabled (mouth, ears, eyes, cheeks, and
                                // nose available):

                                //  L E F T     E Y E     C O N T O U R    ( 16     P O I N T S )
                                val leftEye = face.getContour(FaceContour.LEFT_EYE)
                                leftEye?.let {

                                }

                                val leftEyepoints = leftEye.points
                                val leSize = leftEyepoints.size
                                val lezero = leftEyepoints[0]
                                val lefour = leftEyepoints[4]
                                val leeight = leftEyepoints[8]
                                val letwelve = leftEyepoints[12]


                                if (dummy1 == null) {
                                    dummy1 = leftEyepoints[0]
                                }
                                if (dummy2 == null) {
                                    dummy2 = leftEyepoints[4]
                                }
                                if (dummy3 == null) {
                                    dummy3 = leftEyepoints[8]
                                }
                                if (dummy4 == null) {
                                    dummy4 = leftEyepoints[12]
                                }

                                Log.d(TAG, "Left Eye Position :  $lezero , $lefour , $leeight, $letwelve")
                                Log.d(TAG, " Number of points :$dummy4")

                                val diff_x = lefour.x - letwelve.x
                                val diff_y = lefour.y - letwelve.y

                                //  R I G H T      E Y E     C O N T O U R    ( 16     P O I N T S )
                                val rightEye = face.getContour(FaceContour.RIGHT_EYE)
                                rightEye?.let {

                                }

                                val rightEyepoints = rightEye.points
                                val reSize = rightEyepoints.size
                                val rezero = rightEyepoints[0]
                                val refour = rightEyepoints[4]
                                val reeight = rightEyepoints[8]
                                val retwelve = rightEyepoints[12]

                                Log.d(TAG, "Right Eye Position : $rezero , $refour , $reeight , $retwelve")

                                //////////////////////////////////////////////////

                                val roi: org.opencv.core.Rect = org.opencv.core.Rect(lezero.x.toInt(), lefour.y.toInt(), leeight.x.toInt(), letwelve.y.toInt())

                                val cropped = Mat(roi.size(), CvType.CV_8UC1)


                                Log.d(TAG, "ROI : $cropped")

                                val croppedbmp = cropped?.height()?.let { createBitmap(cropped?.width(), it, Bitmap.Config.ARGB_8888) }

                                Utils.matToBitmap(cropped, croppedbmp)

                                Imgproc.adaptiveThreshold(cropped, cropped , 255.0, Imgproc.ADAPTIVE_THRESH_MEAN_C , Imgproc.THRESH_BINARY , 15, 40.0)

                                Log.d(TAG, "CROPPED : $cropped")

                                


                                //////////////////////////////////////////////////


//                                val croppedbmp = cropped?.height()?.let { createBitmap(cropped?.width() , it, Bitmap.Config.ARGB_8888) }
//                                Utils.matToBitmap(cropped, croppedbmp)


//                                fun File.writeBitmap(bitmap: Bitmap, format: Bitmap.CompressFormat, quality: Int) {
//                                    outputStream().use { out ->
//                                        bitmap.compress(format, quality, out)
//                                        out.flush()
//                                    }
//                                }
//
//                                File(Environment.getExternalStorageDirectory().absolutePath, "map.png").writeBitmap(croppedbmp, Bitmap.CompressFormat.PNG, 85)

                                /////////////////////////////////////////////////////////////////////////////////


                                //  R I G H T      E Y E     L A N D M A R K    ( 1     P O I N T  )
//                                val rightEyePosition = face.getLandmark(FaceLandmark.RIGHT_EYE)
//                                rightEyePosition?.let{
//                                    val posre = rightEyePosition.position
//                                    Log.d(TAG,"Right Eye Landmark : $posre")
//                                }
                                //  L E F T      E Y E     L A N D M A R K    ( 1     P O I N T  )
//                                val leftEyePosition = face.getLandmark(LEFT_EYE)
//                                leftEyePosition?.let{
//                                    val posle = leftEyePosition.position
//                                    Log.d(TAG,"Left Eye Landmark : $posle")
//                                }

                                //  L E F T     E Y E B R O W      T O P     ( 5     P O I N T S )
//                                val leftEyeBrowTop = face.getContour(FaceContour.LEFT_EYEBROW_TOP)
//                                leftEyeBrowTop?.let{
//                                    val leftEyeBrowPointsTop = leftEyeBrowTop.points
//                                    Log.d(TAG,"Left Eye Brow Points Top : $leftEyeBrowPointsTop")
//                                }

                                //  L E F T     E Y E B R O W      B O T T O M     ( 5     P O I N T S )
//                                val leftEyeBrowBottom = face.getContour(FaceContour.LEFT_EYEBROW_BOTTOM)
//                                leftEyeBrowBottom?.let{
//                                    val leftEyeBrowPointsBottom = leftEyeBrowBottom.points
//                                    Log.d(TAG,"Left Eye Brow Points Bottom : $leftEyeBrowPointsBottom")
//                                }
//
//
                                //  R I G H T     E Y E B R O W      T O P     ( 5     P O I N T S )
//                                val rightEyeBrowTop = face.getContour(FaceContour.RIGHT_EYEBROW_TOP)
//                                rightEyeBrowTop?.let{
//                                    val rightEyeBrowPointsTop = rightEyeBrowTop.points
//                                    Log.d(TAG,"Right Eye Brow Points Top : $rightEyeBrowPointsTop")
//
//                                }
//

                                // If classification was enabled:
//                                if (face.smilingProbability != null) {
//                                    val smileProb = face.smilingProbability
//                                }
//                                if (face.rightEyeOpenProbability != null) {
//                                    val rightEyeOpenProb = face.rightEyeOpenProbability
//                                    Log.d(TAG,"Right Open Probability : $rightEyeOpenProb")
//                                }
//                                if (face.leftEyeOpenProbability != null) {
//                                    val leftEyeOpenProb = face.leftEyeOpenProbability
//                                    Log.d(TAG,"Left Open Probability : $leftEyeOpenProb")
//                                }
//
//
                                // If face tracking was enabled:
//                                if (face.trackingId != null) {
//                                    val id = face.trackingId
//                                }
//
                            }


//                            val element:Mat  = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(15.0, 15.0), Point (0.0, 0.0));
//                            val dst:Mat = Mat()
//                            Imgproc.morphologyEx(mat2, dst, Imgproc.MORPH_TOPHAT, element, Point(0.0 , 0.0));


                            listener(faces.size.toDouble())

                            // [END get_face_info]
                            // [END_EXCLUDE]
                        }

                        .addOnCompleteListener{
                            imageProxy.close()
                        }

                        .addOnFailureListener { e ->
                            e.printStackTrace()
                        }

                // [END run_detector]

        }

    }


        /*
    private class YourImageAnalyzer : Analyzer {

        @SuppressLint("UnsafeExperimentalUsageError")
        override fun analyze(imageProxy: ImageProxy) {
            val mediaImage = imageProxy.image
            if (mediaImage != null) {
                val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)


            }

        }
    }

*/

        ////////////   [  F A C E      D E T E C T I O N  ]   ////////////


        // [END mlkit_face_list]
/*
    private fun runFaceContourDetection() {
        val image = InputImage.fromBitmap(mSelectedImage, 0)
        val options = FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
                .build()
        mFaceButton.setEnabled(false)
        val detector = FaceDetection.getClient(options)
        detector.process(image)
                .addOnSuccessListener { faces ->
                    mFaceButton.setEnabled(true)
                    processFaceContourDetectionResult(faces)
                }
                .addOnFailureListener { e -> // Task failed with an exception
                    mFaceButton.setEnabled(true)
                    e.printStackTrace()
                }
    }

    private fun processFaceContourDetectionResult(faces: List<Face>) {
        // Task completed successfully
        if (faces.size == 0) {
            showToast("No face found")
            return
        }
        mGraphicOverlay.clear()
        for (i in faces.indices) {
            val face = faces[i]
            val faceGraphic = FaceContourGraphic(mGraphicOverlay)
            mGraphicOverlay.add(faceGraphic)
            faceGraphic.updateFace(face)
        }
    }

 */







    }


//    /** Processes ImageProxy image data, e.g. used for CameraX live preview case.  */
//    @RequiresApi(VERSION_CODES.KITKAT)
//    @Throws(MlKitException::class)
//   fun processImageProxy(image: ImageProxy?, graphicOverlay: GraphicOverlay?)
//
//    /** Stops the underlying machine learning model and release resources.  */
//    fun stop()

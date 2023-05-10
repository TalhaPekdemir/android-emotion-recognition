package com.tp.cameraxemotionrecognition;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.media.Image;
import android.util.Log;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetector;
import com.tp.cameraxemotionrecognition.ml.EmotionModel;

import org.tensorflow.lite.support.model.Model;
import org.tensorflow.lite.DataType;
import org.tensorflow.lite.gpu.CompatibilityList;
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class FaceDetectorAnalyzer implements ImageAnalysis.Analyzer{
    private static final String TAG = "FaceDetector";
    private final FaceDetector faceDetector;
    private final Context context;
    private final ImageView imageView;
    private final TextView textView;
    private final int imageSize = 224;
    private final String[] classes = {"anger", "contempt", "disgust", "fear", "happy", "neutral", "sad", "surprise"};
    private final int numThreads = 4;
    public FaceDetectorAnalyzer(FaceDetector faceDetector, Context context, ImageView imageView, TextView textView){
        this.faceDetector = faceDetector;
        this.context = context;
        this.imageView = imageView;
        this.textView = textView;
    }
    public String classify(Bitmap image) {
        String classWithConf = null;

        try {
            // TODO her seferinde yapmak yerine detector oluştuğunda oluştur
            // Initialize interpreter with GPU delegate
            Model.Options options;
            CompatibilityList compatList = new CompatibilityList();

            if(compatList.isDelegateSupportedOnThisDevice()){
                // if the device has a supported GPU, add the GPU delegate
                options = new Model.Options.Builder().setDevice(Model.Device.GPU).build();
                Log.d(TAG, "classify: GPU usable");
            } else {
                // if the GPU is not supported, run on specified amount of threads
                options = new Model.Options.Builder().setNumThreads(numThreads).build();
                Log.d(TAG, "classify: GPU is not usable setting thread count to " + numThreads);
            }

            EmotionModel model = EmotionModel.newInstance(context, options);

            // Creates inputs for reference.
            TensorBuffer inputFeature0 = TensorBuffer.createFixedSize(new int[]{1, 224, 224, 3}, DataType.FLOAT32);

            // 4 byte float, imagesize * imagesize, 3 layer RGB
            ByteBuffer byteBuffer = ByteBuffer.allocateDirect(4 * imageSize * imageSize * 3);
            byteBuffer.order(ByteOrder.nativeOrder());

            int[] intValues = new int[imageSize * imageSize];
            image.getPixels(intValues, 0, image.getWidth(), 0, 0, image.getWidth(), image.getHeight());
            int pixel = 0;

            // piksellerden rgb çıkart
            for (int i = 0; i < imageSize; i++) {
                for (int j = 0; j < imageSize; j++) {
                    int val = intValues[pixel++]; //RGB
                    // Modelde rescaling layer yoksa 255'e böl [0 - 255] aralığına al
                    byteBuffer.putFloat(((val >> 16) & 0xFF) * (1.f / 1));
                    byteBuffer.putFloat(((val >> 8) & 0xFF) * (1.f / 1));
                    byteBuffer.putFloat((val & 0xFF) * (1.f / 1));
                }
            }

            inputFeature0.loadBuffer(byteBuffer);

            // Runs model inference and gets result.
            EmotionModel.Outputs outputs = model.process(inputFeature0);
            TensorBuffer outputFeature0 = outputs.getOutputFeature0AsTensorBuffer();

            // model sonuçlarını al
            float[] confidences = outputFeature0.getFloatArray();

            // en yüksek confidence skorunu bul
            int maxPos = 0;
            float maxConfidence = 0f;
            for (int i = 0; i < confidences.length; i++) {
                if(confidences[i] > maxConfidence){
                    maxConfidence = confidences[i];
                    maxPos = i;
                }
            }

            classWithConf = classes[maxPos] + " %" + maxConfidence * 100 + "\n";
            textView.setText(classWithConf); // TODO remove

            // Releases model resources if no longer used.
            model.close();
        } catch (IOException e) {
            Log.e(TAG, "classify: ", e);
        }
        return classWithConf;
    }

    @Override
    public void analyze(@NonNull ImageProxy imageProxy) {
//        image.getFormat() // YUV_420_888 - 35
        @SuppressLint("UnsafeOptInUsageError") Image mediaImage = imageProxy.getImage();
        if (mediaImage != null) {
            InputImage image = InputImage.fromMediaImage(mediaImage, imageProxy.getImageInfo().getRotationDegrees());
            Log.d(TAG, "analyze: W:" + image.getWidth() + " H:" + image.getHeight());
            Task<List<Face>> result = faceDetector.process(image)
                    .addOnSuccessListener(new OnSuccessListener<List<Face>>() {
                        @Override
                        public void onSuccess(List<Face> faces) {
                            if(faces != null){
                                List<String> emotionsList = new ArrayList<>();
                                for(Face face : faces){
                                    Rect faceRect = face.getBoundingBox();
//                                    Log.d(TAG, "onSuccess: " + faceRect);
                                    @SuppressLint("UnsafeOptInUsageError") Bitmap fullImage = BitmapUtils.getBitmap(imageProxy);
                                    // TODO fullImage can be null
                                    Bitmap croppedImage = Utils.cropBitmap(fullImage, faceRect);
//                                    Log.d(TAG, "onSuccess: cropped image: w: " + croppedImage.getWidth()
//                                            + " h:" + croppedImage.getHeight());
                                    Bitmap resizedImage = Bitmap.createScaledBitmap(croppedImage, imageSize, imageSize, false);
                                    // TODO debug bittikten sonra sil
                                    imageView.setImageBitmap(resizedImage);
                                    emotionsList.add(classify(resizedImage));
                                }
                                CharSequence[] charSequences = emotionsList.toArray(new CharSequence[emotionsList.size()]);
                                String text = "Faces: " + faces.size() + "\n" + Arrays.toString(charSequences);
                                textView.setText(text);
                            }
                        }
                    })
                    .addOnFailureListener(new OnFailureListener() {
                        @Override
                        public void onFailure(@NonNull Exception e) {
                            e.printStackTrace();
                        }
                    }).addOnCompleteListener(new OnCompleteListener<List<Face>>() {
                        @Override
                        public void onComplete(@NonNull Task<List<Face>> task) {
                            // Sadece son frame tutulduğu için frame içinde face detection
                            // başarılı veya başarısız olmadan yeni frame almak için.
                            // Aksi takdirde image already closed hatası (metot sonunda çağrılırsa),
                            // yeni frame alamama (failure ve success eventleri içinde çağrılırsa)
                            // gerçekleşir.
                            imageProxy.close();
                        }
                    });
        }
    }
}

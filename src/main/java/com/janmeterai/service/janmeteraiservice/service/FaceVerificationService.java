package com.janmeterai.service.janmeteraiservice.service;

import com.janmeterai.service.janmeteraiservice.model.FaceMatchResponse;
import jakarta.annotation.PostConstruct;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.MatOfInt;
import org.opencv.core.Rect;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.FaceDetectorYN;
import org.opencv.objdetect.FaceRecognizerSF;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

@Service
public class FaceVerificationService {
    private static final Logger log = LoggerFactory.getLogger(FaceVerificationService.class);
    private static final int MINIMUM_RECOGNITION_IMAGE_SIDE = 480;
    private static final double GEOMETRY_SUPPORTED_EMBEDDING_FLOOR = .30d;
    private static final double MINIMUM_LANDMARK_SIMILARITY = .80d;
    private static final double EMBEDDING_WEIGHT = .82d;
    private static final double LANDMARK_WEIGHT = 1d - EMBEDDING_WEIGHT;
    private static final float PRIMARY_DETECTION_THRESHOLD = .72f;
    private static final float RELAXED_DETECTION_THRESHOLD = .52f;
    private final double threshold;
    private FaceDetectorYN detector;
    private FaceRecognizerSF recognizer;

    public FaceVerificationService(@Value("${janmeter.face-match.cosine-threshold:0.363}") double threshold) {
        this.threshold = threshold;
    }

    @PostConstruct
    synchronized void initialize() {
        try {
            nu.pattern.OpenCV.loadLocally();
            Path detectorModel = materialize("models/face_detection_yunet_2023mar.onnx");
            Path recognitionModel = materialize("models/face_recognition_sface_2021dec_int8.onnx");
            detector = FaceDetectorYN.create(detectorModel.toString(), "", new Size(320, 320),
                    PRIMARY_DETECTION_THRESHOLD, .3f, 5000);
            recognizer = FaceRecognizerSF.create(recognitionModel.toString(), "");
        } catch (Exception exception) {
            throw new IllegalStateException("Face verification models could not be initialized", exception);
        }
    }

    public synchronized FaceMatchResponse match(MultipartFile referenceFile, MultipartFile liveFile) {
        validate(referenceFile, "Aadhaar reference");
        validate(liveFile, "Live capture");
        Mat reference = null, live = null, mirroredLive = new Mat();
        List<Mat> referenceFeatures = new ArrayList<>(), liveFeatures = new ArrayList<>(),
                mirroredLiveFeatures = new ArrayList<>();
        try {
            reference = decode(referenceFile.getBytes());
            live = decode(liveFile.getBytes());
            referenceFeatures = features(reference, "Aadhaar reference photo");
            liveFeatures = features(live, "live capture");

            double normalScore = bestScore(referenceFeatures, liveFeatures);
            Core.flip(live, mirroredLive, 1);
            mirroredLiveFeatures = features(mirroredLive, "mirrored live capture");
            double mirroredScore = bestScore(referenceFeatures, mirroredLiveFeatures);
            double embeddingScore = Math.max(normalScore, mirroredScore);
            double normalLandmarks = landmarkSimilarity(reference, live);
            double mirroredLandmarks = landmarkSimilarity(reference, mirroredLive);
            double landmarkScore = Math.max(normalLandmarks, mirroredLandmarks);
            boolean geometryMaySupport = embeddingScore >= GEOMETRY_SUPPORTED_EMBEDDING_FLOOR
                    && landmarkScore >= MINIMUM_LANDMARK_SIMILARITY;
            double geometrySupportedScore = embeddingScore * EMBEDDING_WEIGHT
                    + landmarkScore * LANDMARK_WEIGHT;
            double score = geometryMaySupport
                    ? Math.max(embeddingScore, geometrySupportedScore)
                    : embeddingScore;
            boolean matched = score >= threshold;
            log.info("Face comparison completed: confidence={}, embedding={}, normalEmbedding={}, mirroredEmbedding={}, "
                            + "landmarks={}, normalLandmarks={}, mirroredLandmarks={}, threshold={}, geometrySupported={}, matched={}",
                    round(score), round(embeddingScore), round(normalScore), round(mirroredScore),
                    round(landmarkScore), round(normalLandmarks), round(mirroredLandmarks), threshold,
                    geometryMaySupport, matched);
            String message = matched
                    ? "Face matched the Aadhaar reference photo using facial identity and landmark geometry"
                    : "Face could not be confidently matched (similarity " + round(score)
                    + ", required " + threshold + "). Look straight at the camera in even light and try again";
            return new FaceMatchResponse(matched, round(score), threshold, message);
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "The Aadhaar and live face images could not be compared", exception);
        } finally {
            release(reference, live, mirroredLive);
            release(referenceFeatures); release(liveFeatures); release(mirroredLiveFeatures);
        }
    }

    private List<Mat> features(Mat source, String label) {
        Mat prepared = prepareForRecognition(source);
        Mat face = null, aligned = new Mat(), lightingCorrected = new Mat(), sharpened = new Mat();
        List<Mat> features = new ArrayList<>();
        try {
            face = detectSingle(prepared, label);
            recognizer.alignCrop(prepared, face, aligned);
            features.add(featureFrom(aligned));
            correctLighting(aligned, lightingCorrected);
            features.add(featureFrom(lightingCorrected));
            sharpen(aligned, sharpened);
            features.add(featureFrom(sharpened));
            return features;
        } catch (RuntimeException exception) {
            release(features);
            throw exception;
        } finally {
            release(prepared, face, aligned, lightingCorrected, sharpened);
        }
    }

    private Mat featureFrom(Mat alignedFace) {
        Mat feature = new Mat();
        recognizer.feature(alignedFace, feature);
        return feature;
    }

    private void correctLighting(Mat source, Mat target) {
        Mat yCrCb = new Mat();
        List<Mat> channels = new ArrayList<>();
        try {
            Imgproc.cvtColor(source, yCrCb, Imgproc.COLOR_BGR2YCrCb);
            Core.split(yCrCb, channels);
            Imgproc.equalizeHist(channels.get(0), channels.get(0));
            Core.merge(channels, yCrCb);
            Imgproc.cvtColor(yCrCb, target, Imgproc.COLOR_YCrCb2BGR);
        } finally {
            release(yCrCb); release(channels);
        }
    }

    private void sharpen(Mat source, Mat target) {
        Mat blurred = new Mat();
        try {
            Imgproc.GaussianBlur(source, blurred, new Size(0, 0), 1.15);
            Core.addWeighted(source, 1.45, blurred, -.45, 0, target);
        } finally {
            blurred.release();
        }
    }

    private double bestScore(List<Mat> references, List<Mat> captures) {
        double best = -1;
        for (Mat reference : references) for (Mat capture : captures) {
            best = Math.max(best, recognizer.match(reference, capture, FaceRecognizerSF.FR_COSINE));
        }
        return best;
    }

    private double landmarkSimilarity(Mat reference, Mat capture) {
        double[] referenceShape = landmarkDescriptor(reference, "Aadhaar reference photo");
        double[] captureShape = landmarkDescriptor(capture, "live capture");
        double difference = 0;
        for (int index = 0; index < referenceShape.length; index++) {
            difference += Math.abs(referenceShape[index] - captureShape[index]);
        }
        double meanDifference = difference / referenceShape.length;
        return Math.max(0d, Math.min(1d, Math.exp(-2.8d * meanDifference)));
    }

    private double[] landmarkDescriptor(Mat source, String label) {
        Mat prepared = prepareForRecognition(source);
        Mat face = null;
        try {
            face = detectSingle(prepared, label);
            float[] values = new float[Math.max(15, face.cols())];
            face.get(0, 0, values);
            double firstEyeX = values[4], firstEyeY = values[5];
            double secondEyeX = values[6], secondEyeY = values[7];
            double deltaX = secondEyeX - firstEyeX, deltaY = secondEyeY - firstEyeY;
            double eyeDistanceSquared = deltaX * deltaX + deltaY * deltaY;
            if (eyeDistanceSquared < 25d) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "Facial landmarks were unclear in the " + label);
            }
            double centerX = (firstEyeX + secondEyeX) / 2d;
            double centerY = (firstEyeY + secondEyeY) / 2d;
            return new double[]{
                    normalizedX(values[8], values[9], centerX, centerY, deltaX, deltaY, eyeDistanceSquared),
                    normalizedY(values[8], values[9], centerX, centerY, deltaX, deltaY, eyeDistanceSquared),
                    normalizedX(values[10], values[11], centerX, centerY, deltaX, deltaY, eyeDistanceSquared),
                    normalizedY(values[10], values[11], centerX, centerY, deltaX, deltaY, eyeDistanceSquared),
                    normalizedX(values[12], values[13], centerX, centerY, deltaX, deltaY, eyeDistanceSquared),
                    normalizedY(values[12], values[13], centerX, centerY, deltaX, deltaY, eyeDistanceSquared)
            };
        } finally {
            release(prepared, face);
        }
    }

    private double normalizedX(double pointX, double pointY, double centerX, double centerY,
                               double deltaX, double deltaY, double eyeDistanceSquared) {
        return ((pointX - centerX) * deltaX + (pointY - centerY) * deltaY) / eyeDistanceSquared;
    }

    private double normalizedY(double pointX, double pointY, double centerX, double centerY,
                               double deltaX, double deltaY, double eyeDistanceSquared) {
        return (-(pointX - centerX) * deltaY + (pointY - centerY) * deltaX) / eyeDistanceSquared;
    }

    private Mat prepareForRecognition(Mat source) {
        int shortestSide = Math.min(source.cols(), source.rows());
        if (shortestSide >= MINIMUM_RECOGNITION_IMAGE_SIDE) return source.clone();
        double scale = MINIMUM_RECOGNITION_IMAGE_SIDE / (double) shortestSide;
        Mat enlarged = new Mat();
        Imgproc.resize(source, enlarged, new Size(), scale, scale, Imgproc.INTER_CUBIC);
        return enlarged;
    }

    public synchronized byte[] extractPassportPhoto(MultipartFile documentImage) {
        validate(documentImage, "Aadhaar document");
        Mat document = null, faces = new Mat(), portrait = null;
        MatOfByte encoded = new MatOfByte();
        try {
            document = decode(documentImage.getBytes());
            detector.setInputSize(document.size());
            detector.detect(document, faces);
            if (faces.rows() == 0) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "The passport-size photo could not be found on the Aadhaar PDF");
            }

            int selected = 0;
            double largest = 0;
            for (int row = 0; row < faces.rows(); row++) {
                float[] face = new float[faces.cols()];
                faces.get(row, 0, face);
                double area = face[2] * face[3];
                if (area > largest) { largest = area; selected = row; }
            }
            float[] face = new float[faces.cols()];
            faces.get(selected, 0, face);
            Rect photoBounds = passportPhotoBounds(face, document.cols(), document.rows());
            portrait = new Mat(document, photoBounds).clone();
            Imgcodecs.imencode(".jpg", portrait, encoded,
                    new MatOfInt(Imgcodecs.IMWRITE_JPEG_QUALITY, 94));
            return encoded.toArray();
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "The passport-size photo could not be extracted from the Aadhaar PDF", exception);
        } finally {
            release(document, faces, portrait, encoded);
        }
    }

    private Rect passportPhotoBounds(float[] face, int imageWidth, int imageHeight) {
        double faceX = face[0], faceY = face[1], faceWidth = face[2], faceHeight = face[3];
        // Preserve the complete printed passport-size photograph: hair/background above,
        // both shoulders below, and the original portrait framing around the face.
        int width = (int)Math.round(faceWidth * 1.72d);
        int height = (int)Math.round(faceHeight * 2.05d);
        int x = (int)Math.round(faceX - faceWidth * .36d);
        int y = (int)Math.round(faceY - faceHeight * .34d);
        x = Math.max(0, x); y = Math.max(0, y);
        width = Math.min(width, imageWidth - x);
        height = Math.min(height, imageHeight - y);
        if (width < 100 || height < 120) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "The passport-size photo on the Aadhaar PDF is too small or unclear");
        }
        return new Rect(x, y, width, height);
    }

    private Mat detectSingle(Mat image, String label) {
        Mat faces = detect(image, PRIMARY_DETECTION_THRESHOLD);
        if (faces.rows() == 0) {
            faces.release();
            faces = detect(image, RELAXED_DETECTION_THRESHOLD);
        }
        if (faces.rows() == 0) {
            faces.release();
            Mat corrected = new Mat();
            try {
                correctLighting(image, corrected);
                faces = detect(corrected, RELAXED_DETECTION_THRESHOLD);
            } finally {
                corrected.release();
            }
        }
        if (faces.rows() == 0) {
            log.warn("Face detection failed for {} (image={}x{}, channels={})",
                    label, image.cols(), image.rows(), image.channels());
            faces.release();
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "No clear face was found in the " + label
                            + ". Keep your full face inside the circle and avoid backlighting");
        }
        if (hasMultipleConfidentFaces(faces)) {
            faces.release();
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "More than one face was found in the " + label);
        }
        Mat result = faces.row(highestConfidenceRow(faces)).clone();
        faces.release();
        return result;
    }

    private Mat detect(Mat image, float scoreThreshold) {
        detector.setInputSize(image.size());
        detector.setScoreThreshold(scoreThreshold);
        Mat faces = new Mat();
        detector.detect(image, faces);
        return faces;
    }

    private int highestConfidenceRow(Mat faces) {
        int selected = 0;
        double highest = -1d;
        for (int row = 0; row < faces.rows(); row++) {
            double[] confidence = faces.get(row, 14);
            double value = confidence == null || confidence.length == 0 ? 0d : confidence[0];
            if (value > highest) {
                highest = value;
                selected = row;
            }
        }
        return selected;
    }

    private boolean hasMultipleConfidentFaces(Mat faces) {
        if (faces.rows() < 2) return false;
        int confident = 0;
        for (int row = 0; row < faces.rows(); row++) {
            double[] score = faces.get(row, 14);
            if (score != null && score.length > 0 && score[0] >= PRIMARY_DETECTION_THRESHOLD) confident++;
        }
        return confident > 1;
    }

    private Mat decode(byte[] bytes) {
        Mat encoded = new MatOfByte(bytes);
        Mat decoded = Imgcodecs.imdecode(encoded, Imgcodecs.IMREAD_COLOR);
        encoded.release();
        if (decoded.empty()) { decoded.release(); throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A face image is invalid"); }
        return decoded;
    }

    private Path materialize(String resourceName) throws Exception {
        ClassPathResource resource = new ClassPathResource(resourceName);
        String suffix = resourceName.substring(resourceName.lastIndexOf('.'));
        Path file = Files.createTempFile("janmeter-face-model-", suffix);
        try (var input = resource.getInputStream()) { Files.copy(input, file, StandardCopyOption.REPLACE_EXISTING); }
        file.toFile().deleteOnExit();
        return file;
    }

    private void validate(MultipartFile file, String label) {
        if (file == null || file.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, label + " image is required");
        if (file.getSize() > 8L * 1024 * 1024) throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, label + " image is too large");
    }

    private double round(double value) { return Math.round(value * 10_000d) / 10_000d; }
    private void release(Mat... values) { for (Mat value : values) if (value != null) value.release(); }
    private void release(List<Mat> values) { for (Mat value : values) if (value != null) value.release(); }
}

package com.example.galleryproject;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;

import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.widget.Toast;

import com.example.galleryproject.Database.AppDatabase;
import com.example.galleryproject.Database.AppExecutors;
import com.example.galleryproject.Database.Entity.DbImage;
import com.example.galleryproject.Database.Entity.DbImageCollection;
import com.example.galleryproject.Database.Entity.DbImageGroup;
import com.example.galleryproject.Database.Entity.DbLabel;
import com.example.galleryproject.Database.Entity.DbPriority;
import com.example.galleryproject.Database.Entity.DbRepImage;
import com.example.galleryproject.Model.Adapter.DbImageAdapter;
import com.example.galleryproject.Model.Adapter.ImageAdapter;
import com.example.galleryproject.Model.Adapter.ImageCollectionAdapter;
import com.example.galleryproject.Model.Adapter.ImageGroupAdapter;
import com.example.galleryproject.Model.Adapter.LabelAdapter;
import com.example.galleryproject.Model.Category;
import com.example.galleryproject.Model.Image;
import com.example.galleryproject.Model.ImageCollection;
import com.example.galleryproject.Model.ImageGroup;
import com.example.galleryproject.Model.ImageGroupLabelAnalyzer;
import com.example.galleryproject.Model.Label;
import com.example.galleryproject.Model.LabelGroup;
import com.example.galleryproject.Model.SimGroupAlgorithm;
import com.example.galleryproject.Model.TimeGroupAlgorithm;
import com.example.galleryproject.Model.UnitImage;
import com.example.galleryproject.Util.DatabaseUtils;
import com.example.galleryproject.Util.ImageFileLabeler;
import com.example.galleryproject.ui.survey.SurveyDialogFragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import androidx.annotation.WorkerThread;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProviders;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import org.tensorflow.lite.Interpreter;

import java.io.File;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.stream.Collectors;

public class MainActivity extends AppCompatActivity {
    public final int READ_REQUEST_CODE = 1001;
    public int requestCode = 0;

    public static final String BASE_DIRECTORY_PATH = "DCIM/Camera";
    public static final String EXTENSION_TYPE = ".jpg";

    private static final int IMAGE_TIME_RANGE = 7;
    public static final String TENSORFLOW_MODEL_PATH = "kanghee_model.tflite";

    private List<Image> selectedImages;

    private List<Integer> objectPriority = new ArrayList<>();

    private ImageGroupLabelAnalyzer analyzer = new ImageGroupLabelAnalyzer();

    private int imageOrderIdx = 0;
    private List<List<Image>> partedImages;

    private ImageCollectionViewModel collectionViewModel;
    private AppDatabase mDb;

    private List<AsyncTask> tasks = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ActionBar actionBar = getSupportActionBar();
        actionBar.hide();

        requestPermissions(
                new String[]{
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                },
                READ_REQUEST_CODE);

        collectionViewModel = ViewModelProviders.of(this).get(ImageCollectionViewModel.class);
    }

    @Override
    protected void onPause() {
        super.onPause();

        for (AsyncTask task: tasks) {
            task.cancel(false);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        ImageCollection resultCollection = data.getParcelableExtra("ImageCollection");

        collectionViewModel.getImageCollections().remove(resultCollection);
        collectionViewModel.getImageCollections().add(resultCollection);
    }

    @Override
    protected void onResume(){
        super.onResume();

        requestCode = getIntent().getIntExtra("REQUEST_CODE", 0);
        if (requestCode != READ_REQUEST_CODE) {
            return;
        }

        AppExecutors.getInstance().diskIO().execute(()->{
            List<DbPriority> priorities = mDb.dbPriorityDao().getAll();
            if (priorities.size() != 4) {
                return;
            }

            objectPriority = new ArrayList<>();
            objectPriority.add(0);
            objectPriority.add(0);
            objectPriority.add(0);
            objectPriority.add(0);
            objectPriority.add(0);

            for (DbPriority priority : priorities) {
                objectPriority.set(priority.rank, priority.category);
            }

            objectPriority.set(4, Category.ETC);
            int[] priority = objectPriority.stream()
                    .mapToInt(Integer::intValue)
                    .toArray();

            ImageGroupLabelAnalyzer.setLabelPriority(priority);

            selectedImages = getImagesIfNotProcessed();
            imageOrderIdx = 0;
            partedImages = splitImagesInDayRange(selectedImages, IMAGE_TIME_RANGE);

            if (partedImages.size() > 0) {
                Image[] images = partedImages.get(imageOrderIdx).stream()
                        .toArray(Image[]::new);

                tasks.add(new ImageGroupAsyncTask(() -> {}).execute(images));
            }
        });
    }

    private void initAfterPermissionGranted() {
        mDb = AppDatabase.getInstance(this);

        initLayout();

        AppExecutors.getInstance()
                .diskIO()
                .execute(() -> {
                    fetchImageCollections();

                    // 이미지 파일 목록 추출
                    selectedImages = getImagesIfNotProcessed();
                    imageOrderIdx = 0;
                    partedImages = splitImagesInDayRange(selectedImages, IMAGE_TIME_RANGE);

                    List<DbPriority> priorities = mDb.dbPriorityDao().getAll();
                    if (priorities.size() != 4) {
                        mDb.dbPriorityDao().deleteAll();
                        AppExecutors.getInstance().mainThread().execute(() -> {
                            SurveyDialogFragment survey = SurveyDialogFragment.getInstance();
                            survey.show(this.getSupportFragmentManager(), "Survey Dialog");
                            survey.setSurveyClickListener((ArrayList<String> categories) -> {
                                for (int i = 0; i < categories.size(); i++) {
                                    String category = categories.get(i);
                                    int categoryInt = Category.getValue(category);
                                    objectPriority.add(categoryInt);

                                    DbPriority priority = new DbPriority();
                                    priority.setCategory(categoryInt);
                                    priority.setRank(i);
                                    AppExecutors.getInstance().diskIO().execute(() -> {
                                        mDb.dbPriorityDao().insert(priority);
                                    });
                                }

                                objectPriority.add(Category.ETC);

                                int[] priority = objectPriority.stream()
                                        .mapToInt(Integer::intValue)
                                        .toArray();

                                ImageGroupLabelAnalyzer.setLabelPriority(priority);

                                if (partedImages.size() > 0) {
                                    Image[] images = partedImages.get(imageOrderIdx).stream()
                                            .toArray(Image[]::new);
                                    tasks.add(new ImageGroupAsyncTask(() -> {}).execute(images));
                                }
                            });
                        });
                    } else {
                        objectPriority = new ArrayList<>();
                        objectPriority.add(0);
                        objectPriority.add(0);
                        objectPriority.add(0);
                        objectPriority.add(0);
                        objectPriority.add(0);

                        for (DbPriority priority : priorities) {
                            objectPriority.set(priority.rank, priority.category);
                        }

                        objectPriority.set(4, Category.ETC);
                        int[] priority = objectPriority.stream()
                                .mapToInt(Integer::intValue)
                                .toArray();

                        ImageGroupLabelAnalyzer.setLabelPriority(priority);

                        if (partedImages.size() > 0) {
                            Image[] images = partedImages.get(imageOrderIdx).stream()
                                    .toArray(Image[]::new);

                            tasks.add(new ImageGroupAsyncTask(() -> {}).execute(images));
                        }
                    }
                });
    }

    private List<Image> getImagesIfNotProcessed(){
        List<File> files = getListOfFile();
        List<Image> targetImages = files.stream()
                .map(UnitImage::new)
                .collect(Collectors.toList());

        // 이미 데이터베이스에 저장되어 있는 파일 제거
        List<DbImage> dbImages = mDb.dbImageDao().getAll();
        List<Image> newImages = dbImages.stream()
                .map(x -> new DbImageAdapter(x))
                .collect(Collectors.toList());

        targetImages.removeIf((image) -> newImages.contains(image));
        return targetImages;
    }

    private void initLayout() {
        setContentView(R.layout.activity_main);

        BottomNavigationView navView = findViewById(R.id.nav_view);
        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        AppBarConfiguration appBarConfiguration =
                new AppBarConfiguration.Builder(
                        R.id.navigation_all, R.id.navigation_timeLine, R.id.navigation_map).build();


        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment);
        NavigationUI.setupActionBarWithNavController(this, navController, appBarConfiguration);
        NavigationUI.setupWithNavController(navView, navController);
    }

    @WorkerThread
    private void fetchImageCollections() {
        if (mDb == null) {
            return;
        }

        AppExecutors.getInstance().mainThread().execute(() ->
            collectionViewModel.getImageCollections().clear(true));

        int offset = 0;
        int size = -1;
        while ((offset == 0 && size == -1) ||
               (size != 0)) {
            List<ImageCollection> collections =
                    DatabaseUtils.getCollectionsFromDbByRange(mDb, 10, offset);

            size = collections.size();
            offset += size;
            if (size > 0) {
                AppExecutors.getInstance().mainThread().execute(() -> {
                    collectionViewModel.insertAll(collections);
                });
            }
        }
    }



    /**
     * 정렬된 image를 일 간격에 맞게 분리함.
     * image는 오름차순.
     *
     * @param images
     * @param days
     * @return
     */
    private List<List<Image>> splitImagesInDayRange(List<Image> images, int days) {
        sortImages(images, 0);

        List<List<Image>> partitioned = new ArrayList<>();

        LocalDateTime line = null;

        int startIdx = 0, finishIdx = 0, i = 0;
        while (i <= images.size()) {
            if (line == null && i == images.size()) {
                i += 1;
                continue;
            }

            if (i == images.size()) {
                List<Image> part = images.subList(startIdx, i);
                partitioned.add(part);
                i += 1;
                continue;
            }

            Image im = images.get(i);
            if (line == null) {
                line = im.getCreationTime();
                i += 1;
                continue;
            }

            LocalDateTime start = line.minusDays(days);
            LocalDateTime imTime = im.getCreationTime();
            if (imTime.isBefore(start)) {
                finishIdx = i;

                List<Image> part = images.subList(startIdx, finishIdx);
                partitioned.add(part);

                startIdx = finishIdx;
                line = null;
                continue;
            }

            i++;
        }

        return partitioned;
    }

    /**
     * 시간 순 정렬
     *
     * @param images
     * @param order  (1: 오름차순, 0: 내림차순)
     */
    private static void sortImages(List<Image> images, int order) {
        // 시간 오름차순으로 정렬
        images.sort((i1, i2) -> {
            LocalDateTime d1 = i1.getCreationTime();
            LocalDateTime d2 = i2.getCreationTime();

            if (order > 0) {
                return d1.compareTo(d2);
            } else {
                return d2.compareTo(d1);
            }
        });
    }

    private List<File> getListOfFile() {
        File baseDirectory = null;

        // 파일 목록 추출
        if (!Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
            Toast.makeText(getApplicationContext(), "Error! No SDCARD Found!", Toast.LENGTH_LONG).show();
        } else {

            // Locate the image folder in your SD Card
            baseDirectory = new File(Environment.getExternalStorageDirectory()
                    + File.separator + BASE_DIRECTORY_PATH);

            // Create a new folder if no folder named SDImageTutorial exist
            baseDirectory.mkdirs();
        }

        if (baseDirectory != null && baseDirectory.isDirectory()) {
            File[] files = baseDirectory.listFiles(
                    (File dir, String name) -> name.toLowerCase().endsWith(EXTENSION_TYPE));

            if (files != null) {
                Log.e("LENGTH", String.valueOf(files.length));
            }

            return Arrays.asList(files);
        }

        return Arrays.asList();
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        getIntent().putExtra("REQUEST_CODE", requestCode);

        switch (requestCode) {
            case READ_REQUEST_CODE: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                    // permission was granted, yay! Do the
                    // contacts-related task you need to do.
                    initAfterPermissionGranted();
                } else {
                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                    System.exit(-1);
                }
                return;
            }

            // other 'case' lines to check for other
            // permissions this app might request.
        }
    }


    class SimGroupAsyncTask extends AsyncTask<Image, Integer, List<ImageGroup>> {
        OnTaskFinishedListener listener;

        public SimGroupAsyncTask(OnTaskFinishedListener listener) {
            this.listener = listener;
        }

        @Override
        public List<ImageGroup> doInBackground(Image... images) {
            List<Image> inputImages = Arrays.asList(images);

            SimGroupAlgorithm algorithm = new SimGroupAlgorithm();
            List<ImageGroup> processedGroups = algorithm.processImages(inputImages);
            return processedGroups;
        }

        @Override
        public void onPostExecute(List<ImageGroup> groups) {
//            Toast.makeText(getActivity().getApplicationContext(), groups.size() + "개로 유사도 그룹 완료.", Toast.LENGTH_SHORT).show();

            ImageCollection result = new ImageCollection(groups);

            ImageGroup[] inputGroups = new ImageGroup[groups.size()];
            groups.toArray(inputGroups);

            if (isCancelled()) {
                return;
            }

            // MLkit labeling thread
            new AsyncLabelingTask((labelGroups) -> {
                // callback
                analyzer.setGroups(groups);
                analyzer.setLabelGroups(labelGroups);
                analyzer.analyze();

                /**
                 * 대표사진 추출
                 */
                Log.e("LABEL_ANALYZER", "size: " + groups.size());
                Log.e("LABEL_ANALYZER", analyzer.toString());

                DeepLearningModel model = new DeepLearningModel(MainActivity.this);
                Interpreter tf_lite = model.getTfliteInterpreter(TENSORFLOW_MODEL_PATH);

                Log.e("getXSize : ", analyzer.getX().size() + "");
                float[][][] input = model.parseModelInput(analyzer.getX());
                float[][] priority = new float[input.length][1];

                tf_lite.run(input, priority);

                for(int i = 0; i < input.length; i++)
                    Log.e("PRIORITY : ", "size : " + input.length + " == " + priority.length + "  /  " + priority[i][0] + "");

                List<Image> repImages = model.getRepImages(analyzer.getRepImageCandidate(), priority);

                // 세 장을 선택
                result.setRepImages(repImages.subList(0, repImages.size() > 3 ? 3 : repImages.size()));


                boolean isResultProcessedAlready = collectionViewModel.getImageCollections().getValue().contains(result);
                if (!isResultProcessedAlready) {
                    collectionViewModel.insert(result);

                    // 생성된 타임라인 저장 thread
                    AppExecutors.getInstance().diskIO().execute(() -> {
                        DbImageCollection newDbImageCollection = new ImageCollectionAdapter(result);

                        long collectionId = mDb.dbImageCollectionDao().insert(newDbImageCollection);

                        // DbImageCollection <- DbImageGroup
                        List<DbImageGroup> newDbImageGroups =
                                result.getGroups().stream()
                                        .map(ImageGroupAdapter::new)
                                        .collect(Collectors.toList());

                        List<Long> dbImageGroupIds =
                                mDb.dbImageCollectionDao()
                                        .insertWithGroups((int) collectionId, newDbImageGroups);

                        List<Long> imageIdsForRepImage = new ArrayList<>();

                        // DbGroup <- DbImage
                        for (int i = 0; i < dbImageGroupIds.size(); i++) {
                            ImageGroup ig = result.getGroups().get(i);
                            List<Image> iImageGroup = ig.getImages();

                            List<DbImage> newDbImages = iImageGroup.stream()
                                    .map(ImageAdapter::new)
                                    .collect(Collectors.toList());

                            List<Long> dbImageIds = new ArrayList<>();
                            Long groupId = dbImageGroupIds.get(i);
                            for(DbImage image: newDbImages) {
                                long dbImageId = mDb.imagesWithImageGroupDao()
                                        .insertImageWithGroupId(groupId, image);
                                dbImageIds.add(dbImageId);

                                // 대표사진 imageId 값 저장
                                for (Image rim: repImages) {
                                    if (rim.equals(image.path)) {
                                        imageIdsForRepImage.add(dbImageId);
                                    }
                                }
                            }

                            // DbImage <- DbLabel
                            List<LabelGroup> labelGroup = labelGroups.get(i);
                            for (int j = 0; j < dbImageIds.size(); j++) {
                                long dbImageId = dbImageIds.get(j);
                                List<Label> labels = labelGroup.get(j).getLabels();
                                List<DbLabel> dbLabels = labels.stream()
                                        .map(LabelAdapter::new)
                                        .collect(Collectors.toList());

                                mDb.imagesWithImageGroupDao()
                                        .insertLabelsWithImageId(dbImageId, dbLabels);

                            }
                        }

                        for (long imageId: imageIdsForRepImage) {
                            DbRepImage repImage = new DbRepImage();

                            mDb.dbRepImageDao()
                                    .insertWithCollectionAndImage(repImage, (int) collectionId, (int) imageId);
                        }
                    });
                }

                listener.onFinished();
            }).execute(inputGroups);
        }
    }


    /**
     * 이미지 타임라인 그룹 생성 쓰레드
     */
    private class ImageGroupAsyncTask extends AsyncTask<Image, Integer, List<ImageGroup>> {
        OnTaskFinishedListener listener;

        private CountDownLatch latch;
        ImageGroupAsyncTask(OnTaskFinishedListener listener) {
            super();
            this.listener = listener;
        }

        @Override
        public List<ImageGroup> doInBackground(Image... images) {

            List<Image> inputImages = Arrays.asList(images);

            // 일정 시간 단위로 나눔
            TimeGroupAlgorithm algorithm = new TimeGroupAlgorithm();
            List<ImageGroup> processedGroups = algorithm.processImages(inputImages);
            publishProgress(processedGroups.size());

            return processedGroups;
        }

        @Override
        public void onPostExecute(List<ImageGroup> groups) {
            // 유사도 클러스터링
            int groupsCount = groups.size();
            latch = new CountDownLatch(groupsCount);

            for (ImageGroup group : groups) {
                if (isCancelled()) {
                    return;
                }

                // 시간 그룹 내에서 유사도 클러스터링 실행
                Image[] timeImages = new Image[group.getImages().size()];
                tasks.add(new SimGroupAsyncTask(() -> startNextImages(latch)).execute(group.getImages().toArray(timeImages)));
            }

            if (groups.size() <= 0) {
                startNextImages(latch);
            }
        }

        private void startNextImages(CountDownLatch latch) {
            if (latch.getCount() > 0) {
                latch.countDown();
            }

            if (latch.getCount() == 0) {
                imageOrderIdx += 1;

                if (imageOrderIdx >= partedImages.size()) {
                    return;
                }

                // 다음 시간 클러스터링 실행
                int nextImagesCount = partedImages.get(imageOrderIdx).size();

                Image[] nextImages = new Image[nextImagesCount];
                partedImages.get(imageOrderIdx).toArray(nextImages);

                tasks.add(new ImageGroupAsyncTask(() -> {}).execute(nextImages));
            }
        }
    }

    /**
     * Thread for Async MLKit DbImage Labeler
     * labeling 하고자 하는 이미지 -> label list
     */
    class AsyncLabelingTask extends AsyncTask<ImageGroup, Void, List<List<LabelGroup>>> {
        List<String> filenames = new ArrayList<>();

        private OnLabelTaskFinishedListener listener;

        AsyncLabelingTask(OnLabelTaskFinishedListener listener) {
            this.listener = listener;
        }

        @Override
        protected List<List<LabelGroup>> doInBackground(ImageGroup... groups) {
            List<List<LabelGroup>> allLabelGroups = new ArrayList<>();
            for (ImageGroup group: groups) {
                if (isCancelled()) {
                    break;
                }
                List<Image> images = group.getImages();
                List<LabelGroup> labelGroups = images.stream()
                        .map(x -> processImagesWithMlkit(x))
                        .collect(Collectors.toList());
                allLabelGroups.add(labelGroups);
            }

            return allLabelGroups;
        }

        @Override
        protected void onPostExecute(List<List<LabelGroup>> labels) {
            super.onPostExecute(labels);
            if (isCancelled()) {
                return;
            }

            listener.onFinished(labels);
        }

        LabelGroup resultLabelGroup;
        private LabelGroup processImagesWithMlkit(Image imageFile) {
            final CountDownLatch latch = new CountDownLatch(1);
            File file = imageFile.getFile();

            ImageFileLabeler imageFileLabeler = new ImageFileLabeler(file, new ImageFileLabeler.ImageFileLabelerListener() {
                @Override
                public void onSuccess(File file, List<Label> labels) {
                    resultLabelGroup = new LabelGroup(labels);
                    filenames.add(file.getName());

                    latch.countDown();
                }

                @Override
                public void onFailure(File file) {
                    latch.countDown();
                    Log.e("ImageFileLabeler", file.getName());
                }
            });

            imageFileLabeler.process();

            try {
                // labeler가 종료되면 return 할 수 있도록
                latch.await();
            } catch (InterruptedException e) {
                e.printStackTrace();
                Log.e("MLKIT_ASYNC_TASK", e.getMessage());
            }

            return resultLabelGroup;
        }

    }

    interface OnTaskFinishedListener {
        void onFinished();
    }

    interface OnLabelTaskFinishedListener {
        void onFinished(List<List<LabelGroup>> groups);
    }
}

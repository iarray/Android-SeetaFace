/**
 * 采用中科院山世光开源的SeetaFaceEngine实现android上的人脸检测与对齐、识别
 * BSD license
 * 广州炒米信息科技有限公司
 * www.cume.cc
 * 吴祖玉
 * wuzuyu365@163.com
 * 2016.11.9
 *
 */

package seetaface;


import java.io.File;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.net.Uri;
import android.nfc.Tag;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import cc.cume.R;

public class MainActivity extends Activity {
	final String tag = "SeetafacesoPrj"; 
	ImageView imv1,imv2, imvFace1, imvFace2, imvcmp; 
	TextView textView1,textView2; 

	Button btn1,btn2,button_auto;   
	Button btn_proc; 
	
	Context mContext; 
	String mFaceModelDir; //人脸正面检测模型
	int mCurIndex = 0; //当前是选中了第几个图，1或2
	Boolean mModelFileExist = false; //人脸检测模型文件存在否，如果不存在，则不能继续下去
	byte[] mFaceByte1, mFaceByte2; 
	Bitmap mOriginBmp; //原位图
	Bitmap mDrawBmp; //显示用的位图，
	int mWidth1, mHeight1, mCh1; //照片1的宽度高度、通道
	int mWidth2, mHeight2, mCh2; //照片2的宽度高度、通道
	int mFaceNum1, mFaceNum2; //人脸数
	CMSeetaFace[] mFaces1, mFaces2; //检测出来的人脸特征
	List<String> mImgPaths = new ArrayList<String>(); //要自动处理的路径列表
	SeetaFace jni; 
	Bitmap mFaceBmp1,mFaceBmp2;

	Handler handler = new Handler();
	//要用handler来处理多线程可以使用runnable接口，这里先定义该接口
	//线程中运行该接口的run函数
	Runnable update_thread = new Runnable()
	{
		public void run()
		{
			if (null == mFaces1 || mFaces1.length < 1) {
				textView1.setText("照片1没有人脸，先选择一张有人照片");
				return; 
			}
			
			if (null == mImgPaths || mImgPaths.isEmpty()) {
				Log.i("update_thread", "没有了");
				textView1.setText("DCIM没有待比对的照片");
				return; 
			}else{
				String tPath = mImgPaths.get(0);
				mImgPaths.remove(0);

				imvcmp.setImageBitmap(null); 
				 				
				//自动检测所有照片，并与照片0比对人脸，在照片1上显示人脸
				detectAndCompareFace(tPath, 1);
				
				File file= new File(tPath); 
				file.getName(); 
				CharSequence cs = textView1.getText();
				String s = cs.toString(); 
				s += "，剩余:"+mImgPaths.size()+"\n照片名:"+file.getName();
				textView1.setText(s);
				Log.i("update_thread", "剩下:"+mImgPaths.size()+", tPath="+tPath);
			}

			//延时1ms后又将线程加入到线程队列中
			handler.postDelayed(update_thread, 1);
		}
	};

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);   

		btn1 = (Button)findViewById(R.id.button1);
		btn2 = (Button)findViewById(R.id.button2); 
		button_auto = (Button)findViewById(R.id.button_auto); 
		btn_proc = (Button)findViewById(R.id.button_proc); 
		mContext = MainActivity.this; 
		imv1 = (ImageView)findViewById(R.id.imageView1);
		imv2 = (ImageView)findViewById(R.id.imageView2);
		
		imvcmp = (ImageView)findViewById(R.id.ImageViewCmp);
		
		textView1 = (TextView)findViewById(R.id.textView1); 

		imvFace1 = (ImageView)findViewById(R.id.ImageView01);
		imvFace2 = (ImageView)findViewById(R.id.ImageView02);

		//先显示空白图片
		imv1.setImageBitmap(null);
		imv2.setImageBitmap(null);
		imvFace1.setImageBitmap(null);
		imvFace2.setImageBitmap(null);
		imvcmp.setImageBitmap(null);
		mFaceNum1 = 0;  
		mFaceNum2 = 0;  

		//正面人脸检测、对齐、识别模型文件目录，应该把这3个文件放在/sdcard/目录下 		 
		mFaceModelDir = Environment.getExternalStorageDirectory().getAbsolutePath();
		Boolean tInitOk = false; 
		try{ 
			jni = new SeetaFace();	 
			tInitOk = jni.init(mFaceModelDir); 			 
		}catch(Exception e){

		} 
		
		Log.i(tag, "tInitOk="+tInitOk); 

		mFaceBmp1 = Bitmap.createBitmap(256,256, Config.ARGB_8888);
		mFaceBmp2 = Bitmap.createBitmap(256,256, Config.ARGB_8888);
				
		//初始化，输入模型文件的路径

		//产生自动比对的照片路径列表
		String tDirs[] = {"sdcard"}; 

		for(String tDir:tDirs){
			getImgFiles(tDir);
		}
		
		//检查人脸检测模型文件是否存在
		String tPath = mFaceModelDir + "/seeta_fd_frontal_v1.0.bin";
		mModelFileExist = FileUtils.fileIsExists(tPath);
		if(!mModelFileExist){
			textView1.setText("模型不存在，无法检测人脸:"+tPath);
		}else{ 
			//检查人脸对齐模型文件是否存在
			tPath = mFaceModelDir + "/seeta_fa_v1.1.bin";
			mModelFileExist = FileUtils.fileIsExists(tPath);
			if(!mModelFileExist){ 
				textView1.setText("模型不存在，无法检测人脸:"+tPath);
			}else{
				//检查人脸识别模型文件是否存在
				tPath = mFaceModelDir + "/seeta_fr_v1.0.bin";
				mModelFileExist = FileUtils.fileIsExists(tPath);
				if(!mModelFileExist){ 
					textView1.setText("模型不存在，无法检测人脸:"+tPath);
				}
			}
		}  

		btn_proc.setOnClickListener(new OnClickListener() {			
			@Override
			public void onClick(View v) {
				img_proc();
			} 
		}); 
		
		button_auto.setOnClickListener(new OnClickListener() {			
			@Override
			public void onClick(View v) {
				handler.post(update_thread);
			} 
		}); 

		btn1.setOnClickListener(new OnClickListener() {			
			@Override
			public void onClick(View v) {
				mCurIndex = 0;
				Intent getAlbum = new Intent(Intent.ACTION_GET_CONTENT);
				getAlbum.setType("image/*");
				startActivityForResult(getAlbum, 0);
			}
		});

		btn2.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				mCurIndex = 1;
				Intent getAlbum = new Intent(Intent.ACTION_GET_CONTENT);
				getAlbum.setType("image/*");
				startActivityForResult(getAlbum, 0);
			}
		});
	}

	public void img_proc(){
		Bitmap tBitmap = Bitmap.createBitmap(mOriginBmp.getWidth(), mOriginBmp.getHeight(), Config.ARGB_8888);
		Bitmap tGrayBitmap = Bitmap.createBitmap(mOriginBmp.getWidth(), mOriginBmp.getHeight(), Config.ALPHA_8);
		Log.i(tag, "img_proc, tGrayBitmap.size ="+tGrayBitmap.getWidth()+","+tGrayBitmap.getHeight()); 
		long t=System.currentTimeMillis(); 
		
		//jni.imGamma(mOriginBmp, tBitmap, 0.5f);
		jni.im2gray( mOriginBmp, tGrayBitmap);
		t = System.currentTimeMillis() - t;
		Log.i(tag, "img_proc, t="+t); 
		imv2.setImageBitmap(tGrayBitmap); 
		//imv2.setImageBitmap(tBitmap);
	}
	
	/*
	 * 通过递归得到某一路径下所有的图像文件，保存在mImgPaths里面
	 */
	public void getImgFiles(String vDir){
		if(TextUtils.isEmpty(vDir)){
			return; 
		}
		File root = new File(vDir);
		if(null == root){
			return; 
		}
		File[] files = root.listFiles();
		if(null == files || files.length < 1){
			return; 
		}
		
		for(File file:files){     
			if(file.isDirectory()){
				// 递归调用
				getImgFiles(file.getAbsolutePath());
				
				//System.out.println("显示"+filePath+"下所有子目录及其文件"+file.getAbsolutePath());
			}else{
				if(XUtils.isImageFile(file.getAbsolutePath())){
					mImgPaths.add(file.getAbsolutePath());
				}
				//System.out.println("显示"+filePath+"下所有子目录"+file.getAbsolutePath());
			}     
		}
	}

//	/**
//	 * 根据给出的多个目录列出目录下所有照片，生成待自动比对的照片路径列表
//	 * @param vDirs
//	 * @return
//	 */
//	public List<String> listImgFiles(String[] vDirs){
//		Log.i("listImgFiles", "1"); 
//		List<String> tList = new ArrayList<String>();
//		if(null == vDirs || vDirs.length < 1){
//			return tList;
//		}
//
//		Log.i("listImgFiles", "vDirs.length="+vDirs.length); 
//
//		try{
//			for(String vDir:vDirs){
//				File f = new File(vDir);
//				File[] paths = f.listFiles();
//				for(File xf:paths){
//					String tPath = xf.getCanonicalPath();
//					String tPath2 = tPath.toLowerCase();
//					if(xf.isDirectory()){
//
//					}else{
//						//检测是否图像文件 
//						if(XUtils.isImageFile(tPath)){
//							Log.i("listImgFiles", tPath);
//							tList.add(tPath); 
//						}
//					}
//				}
//			}			
//		}catch(Exception e){
//			e.printStackTrace();
//		}
//
//		return tList; 
//	}

	//	/**
	//	 * 显示人脸比对结果
	//	 */
	//	public void showCompare(){		 
	//		if(mFaceNum1 < 1 || mFaceNum2 < 1){
	//			textView1.setText("要有2个人脸才能比对相似度");
	//			return; 
	//		}
	//		
	//		if (null == mFaces1 || mFaces1.length < 1) {
	//			textView1.setText("照片1没有人脸");
	//			Log.i("showCompare", "mFaces1为空");
	//			return; 
	//		}
	//		if (null == mFaces2 || mFaces2.length < 1) {
	//			textView1.setText("照片2没有人脸");
	//			Log.i("showCompare", "mFaces2为空");
	//			return; 
	//		}
	//		
	//		float tSim = jni.CalcSimilarity(mFaces1[0].features, mFaces2[0].features, mFaceModelDir);
	////		for(int i=0; i<10; i++){
	////			Log.i("showCompare", "feat,i="+i+", "+mFaces1[0].features[i]+","+ mFaces2[0].features[i]);  
	////		}
	//		if(tSim > 0.5){
	//			textView1.setText("相似度:"+tSim+", 应该是一个人");	
	//		}else{
	//			textView1.setText("相似度:"+tSim+", 应该不是一个人");
	//		}
	//	}

	/**
	 * 获取图像的字节数据
	 * @param image
	 * @return
	 */
	public byte[] getPixelsRGBA(Bitmap image) {
		// calculate how many bytes our image consists of
		int bytes = image.getByteCount();

		ByteBuffer buffer = ByteBuffer.allocate(bytes); // Create a new buffer
		image.copyPixelsToBuffer(buffer); // Move the byte data to the buffer

		byte[] temp = buffer.array(); // Get the underlying array containing the

		return temp;
	}

	public void onClick(View v) {
		Intent getAlbum = new Intent(Intent.ACTION_GET_CONTENT);
		getAlbum.setType("image/*");
		startActivityForResult(getAlbum, 0);
	}

	/**
	 * byte转bitmap 
	 * @param b
	 * @return
	 */
	Bitmap Bytes2Bimap(byte[] b) {  
		if (b.length != 0) {  
			return BitmapFactory.decodeByteArray(b, 0, b.length);  
		} else {  
			return null;  
		}  
	}  

	/**
	 * 
	 * 方法名: </br>
	 * 详述: </br>
	 * 开发人员：吴祖玉</br>
	 * 创建时间：2015年12月16日</br>
	 * 
	 * @param vFileName:assets下的文件名
	 */
	public String getAssetFilePath(String vFileName) {
		File dir = mContext.getDir("file", Context.MODE_PRIVATE);
		File soFile = new File(dir, vFileName);
		FileUtils.assetToFile(mContext, vFileName, soFile);

		try {
			return soFile.getAbsolutePath();
		} catch (Exception e) {
		}
		return null;
	}

	protected void onActivityResult(int requestCode, int resultCode, Intent data) {

		if (resultCode != RESULT_OK) { // 此处的 RESULT_OK 是系统自定义得一个常量
			Log.e("TAG->onresult", "ActivityResult resultCode error");
			return;
		}

		//清空提示信息
		textView1.setText("");

		// 外界的程序访问ContentProvider所提供数据 可以通过ContentResolver接口

		// 此处的用于判断接收的Activity是不是你想要的那个
		if (requestCode == 0) {
			Uri originalUri = data.getData(); // 获得图片的uri
			String imgPath = GetPathFromUri4kitkat.getPath(mContext, originalUri);
			Log.i("loadimg", "path="+imgPath); 
			detectAndCompareFace(imgPath, mCurIndex);
		}  		 
	}

	/**
	 * 检测并比对人脸
	 * @param vImgpath:照片路径
	 * @param vIndex:当前是待比对的上下2张照片中的哪一张，只能是0或1
	 */
	public void detectAndCompareFace(String vImgpath, int vIndex){
		String tag = "detectAndCompareFace";
		if(TextUtils.isEmpty(vImgpath)){
			Log.i(tag, "图片路径为空"); 
			return;
		}
		
		long te = System.currentTimeMillis();
		if(vIndex < 0 || vIndex > 1){
			Log.i(tag, "vIndex只能是0或1");
			return; 
		}

		if(!mModelFileExist){
			textView1.setText("人脸检测、识别模型文件不存在，要放在/sdcard/目录下"); 
			return; 
		}

		mCurIndex = vIndex; 
		textView1.setText("");

		mOriginBmp = XUtils.getScaledBitmap(vImgpath, 400);
		
		if(null == mOriginBmp){
			Log.i(tag, "图片无法加载"); 
			return;
		}

		int width = mOriginBmp.getWidth(); 
		int height = mOriginBmp.getHeight();

//		byte[] bs = getPixelsRGBA(mOriginBmp);
//		Log.i(tag, "width="+width+", height="+height+
//				",bs.len="+bs.length); 

//		//计算图像通道数ch
//		int ch = bs.length / (width*height); 
//		if (ch < 1){
//			ch = 1; 
//		} 

		CMSeetaFace[] tRetFaces; 
		long t = System.currentTimeMillis();
//		float tGamma = 0.6f; 
//		Log.i(tag, "tGamma="+tGamma); 
		
		if(0 == mCurIndex){
//			mFaceByte1 = getPixelsRGBA(mOriginBmp);
			mWidth1 = width; 
			mHeight1 = height;
//			mCh1 = mFaceByte1.length / (width*height);
//			//计算图像通道数ch
//			if (mCh1 < 1){
//				mCh1 = 1; 
//			} 
			
			tRetFaces = jni.DetectFaces(mOriginBmp, mFaceBmp1);
			//tRetFaces = jni.GetFaces(bs, width, height, ch, mFaceBmp1);
		}else{
//			mFaceByte2 = getPixelsRGBA(mOriginBmp); 
			mWidth2 = width; 
			mHeight2 = height; 
//			mCh2 = mFaceByte2.length / (width*height);
//			//计算图像通道数ch
//			if (mCh2 < 1){
//				mCh2 = 1; 
//			}
			
			tRetFaces = jni.DetectFaces(mOriginBmp, mFaceBmp2);
			//tRetFaces = jni.GetFaces(bs, width, height, ch, mFaceBmp2);
		}

		t = System.currentTimeMillis() - t;

		int face_num = 0;
		if(null != tRetFaces){
			face_num =  tRetFaces.length; 
		}

		Log.i(tag, "face_num="+face_num); 

		for(int i=0; i<face_num; i++){
			Log.i(tag, "roll,pitch,yaw="+tRetFaces[i].roll_angle+", "+tRetFaces[i].pitch_angle+","+tRetFaces[i].yaw_angle);  
			
			Log.i(tag, "seetaFaces"+i+", pos:"+ 
					tRetFaces[i].left + ", "+
					tRetFaces[i].right + ", "+
					tRetFaces[i].top + ", "+
					tRetFaces[i].bottom + ", ");

			//			for(int j=0; j<10; j++){
			//				Log.i("loadimg", "feat"+j+":"+tRetFaces[i].features[j]); 
			//			}
		}

		if (face_num < 1) {		 			
			if(0 == mCurIndex){
				imv1.setImageBitmap(mOriginBmp);
				imvFace1.setImageBitmap(null);
				mFaceNum1 = 0; 
				mFaces1 = tRetFaces; 
			}else{
				imv2.setImageBitmap(mOriginBmp);
				imvFace2.setImageBitmap(null);
				mFaceNum2 = 0;
				mFaces2 = tRetFaces; 
			}
		}else{
			//大图，显示人脸矩形
			mDrawBmp = mOriginBmp.copy(Config.ARGB_8888, true);
		 
			//Log.i(tag, "mDrawBmp width="+mDrawBmp.getWidth()+", height="+mDrawBmp.getHeight()); 

			Canvas canvas = new Canvas(mDrawBmp);
			Paint paint = new Paint();

			//线条宽度
			int tStokeWid = 1+(width+height)/300; 
			paint.setColor(Color.RED);
			paint.setStyle(Paint.Style.STROKE);//不填充
			paint.setStrokeWidth(tStokeWid);  //线的宽度
			for(int i=0; i<face_num; i++){			 		 
				int left = tRetFaces[i].left;
				int top = tRetFaces[i].top; 
				int right = tRetFaces[i].right;
				int bottom = tRetFaces[i].bottom;
				Log.i("loadimg", "drawRect,i="+i+", "+left+","+right+","+top+","+bottom); 
				canvas.drawRect(left, top, right, bottom, paint);		 		

		 		//画特征点
		 		for(int j=0; j<5; j++){
		 			int px = tRetFaces[i].landmarks[j*2];
		 			int py = tRetFaces[i].landmarks[j*2+1];
			 		//Log.i("loadimg", "j="+j+",px=" + px +", py="+py);
			 		canvas.drawCircle(px, py, tStokeWid, paint);
		 		}
			}

			if(0 == mCurIndex){
				imv1.setImageBitmap(mDrawBmp);	
				imvFace1.setImageBitmap(mFaceBmp1);
				mFaceNum1 = face_num;
				mFaces1 = tRetFaces; 
			}else{
				mFaceNum2 = face_num;
				imv2.setImageBitmap(mDrawBmp);
				imvFace2.setImageBitmap(mFaceBmp2);
				mFaces2 = tRetFaces; 
			}

			//================================比对并显示结果================================

			if (null == mFaces1 || mFaces1.length < 1) {
				textView1.setText("照片1没有人脸");
				Log.i("showCompare", "mFaces1为空");
				return; 
			}
			if (null == mFaces2 || mFaces2.length < 1) {
				textView1.setText("照片2没有人脸");
				Log.i("showCompare", "mFaces2为空");
				return; 
			}

			String msg = ""; 
			float tSim = 0; 
			if(mFaceNum1 > 0 && mFaceNum2 > 0){
				tSim = jni.CalcSimilarity(mFaces1[0].features, mFaces2[0].features);
				msg += "相似度:"+tSim; 
			}

			te = System.currentTimeMillis() - te; 
			msg += "，  时间:"+te+"毫秒"; 

			//			for(int i=0; i<10; i++){
			//				Log.i("showCompare", "feat,i="+i+", "+mFaces1[0].features[i]+","+ mFaces2[0].features[i]);  
			//			}

			textView1.setText(msg);

			int  tCmpRst = tSim > 0.495 ? R.drawable.ok : R.drawable.close;			
			imvcmp.setImageResource(tCmpRst);
		} 
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	} 

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle action bar item clicks here. The action bar will
		// automatically handle clicks on the Home/Up button, so long
		// as you specify a parent activity in AndroidManifest.xml.
		//		int id = item.getItemId();
		//		if (id == R.id.action_settings) {
		//			return true;
		//		}
		return super.onOptionsItemSelected(item);
	}
}

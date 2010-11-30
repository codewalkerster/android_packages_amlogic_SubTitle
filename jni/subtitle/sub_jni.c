////////////////////////////////////////////////////////////////////////////////
// JNI Interface
////////////////////////////////////////////////////////////////////////////////

#include <jni.h>
#include <android/log.h>

#define  LOG_TAG    "sub_jni"
#define  LOGI(...)  __android_log_print(ANDROID_LOG_INFO,LOG_TAG,__VA_ARGS__)
#define  LOGE(...)  __android_log_print(ANDROID_LOG_ERROR,LOG_TAG,__VA_ARGS__)

#ifndef NELEM
#define NELEM(x) ((int) (sizeof(x) / sizeof((x)[0])))
#endif

#include "sub_api.h"
#include <string.h>

JNIEXPORT jobject JNICALL parseSubtitleFile
  (JNIEnv *env, jclass cl, jstring filename, jstring encode)
{
      jclass cls = (*env)->FindClass(env, "com/subtitleparser/SubtitleFile");
      if(!cls){
          LOGE("parseSubtitleFile: failed to get SubtitleFile class reference");
          return NULL;
      }

      jmethodID constr = (*env)->GetMethodID(env, cls, "<init>", "()V");
      if(!constr){
          LOGE("parseSubtitleFile: failed to get  constructor method's ID");
          return NULL;
      }

      jobject obj =  (*env)->NewObject(env, cls, constr);
      if(!obj){
          LOGE("parseSubtitleFile: failed to create an object");
          return NULL;
      }

	  jmethodID mid = (*env)->GetMethodID(env, cls, "appendSubtitle", "(III[BLjava/lang/String;)V");
      if(!mid){
          LOGE("parseSubtitleFile: failed to get method append's ID");
          return NULL;
      }

      const char *nm = (*env)->GetStringUTFChars(env,filename, NULL);
      const char* charset= (*env)->GetStringUTFChars(env,encode, NULL);
      subdata_t * subdata = NULL;
      subdata = internal_sub_open(nm,0,charset);
      if(subdata == NULL){
          LOGE("internal_sub_open failed! :%s",nm);
          goto err2;
      }

      jint i=0, j=0;
      list_t *entry;
      jstring jtext;
      
      char * textBuf = NULL;

      list_for_each(entry, &subdata->list)
      {
          i++;
          subtitle_t *subt = list_entry(entry, subtitle_t, list);
	
		  textBuf = (char *)malloc(subt->text.lines *512);
		  if(textBuf == NULL){
			LOGE("malloc text buffer failed!");
			goto err;
		  }

		memset( textBuf, 0,subt->text.lines *512);

		for (j=0; j< subt->text.lines; j++) {
			strcat(textBuf, subt->text.text[j]);
			strcat(textBuf, "\n");
		}
		
	  jbyteArray array= (*env)->NewByteArray(env,strlen(textBuf));
	  
	  (*env)->SetByteArrayRegion(env,array,0,strlen(textBuf), textBuf);	  
		//jtext = (*env)->NewStringUTF(env, textBuf); //may cause err.
              

		(*env)->CallVoidMethod(env, obj, mid, i, subt->start/90, subt->end/90, array,encode);
		(*env)->DeleteLocalRef (env,array );
		free(textBuf);
	}
	internal_sub_close(subdata);
	(*env)->ReleaseStringUTFChars(env,filename, nm);
	return obj;

err:


      internal_sub_close(subdata);

err2:

      (*env)->ReleaseStringUTFChars(env,filename, nm);


      return NULL;
  }

static JNINativeMethod gMethods[] = {
    /* name, signature, funcPtr */
    { "parseSubtitleFileByJni", "(Ljava/lang/String;Ljava/lang/String;)Lcom/subtitleparser/SubtitleFile;",
            (void*) parseSubtitleFile},
    };

static int registerNativeMethods(JNIEnv* env, const char* className,
                                 const JNINativeMethod* methods, int numMethods)
{
    int rc;
    jclass clazz;
    clazz = (*env)->FindClass(env, className);
    if (clazz == NULL) {
        LOGE("Native registration unable to find class '%s'\n", className);
        return -1;
    }
    if (rc = ((*env)->RegisterNatives(env, clazz, methods, numMethods)) < 0) {
        LOGE("RegisterNatives failed for '%s' %d\n", className, rc);
        return -1;
    }

    return 0;
}

JNIEXPORT jint
JNI_OnLoad(JavaVM* vm, void* reserved)
{
    JNIEnv* env = NULL;
    jclass * localClass;
    LOGE("================= JNI_OnLoad ================\n");

    if ((*vm)->GetEnv(vm, (void**) &env, JNI_VERSION_1_4) != JNI_OK) {
        LOGE("GetEnv failed!");
        return -1;
    }

    if (registerNativeMethods(env, "com/subtitleparser/Subtitle",gMethods, NELEM(gMethods)) < 0){
        LOGE("registerNativeMethods failed!");
        return -1;
    }
    return JNI_VERSION_1_4;
}

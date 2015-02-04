#define LOG_TAG "amSystemControl"

#include <../../../../../vendor/amlogic/frameworks/services/systemcontrol/ISystemControlService.h>

#include <binder/Binder.h>
#include <binder/IServiceManager.h>
#include <utils/Atomic.h>
#include <utils/Log.h>
#include <utils/RefBase.h>
#include <utils/String8.h>
#include <utils/String16.h>
#include <utils/threads.h>
#include <Amsyswrite.h>
#include <unistd.h>

#include <MemoryLeakTrackUtilTmp.h>
#include <fcntl.h>

using namespace android;

class DeathNotifier: public IBinder::DeathRecipient
{
    public:
        DeathNotifier()
        {
        }

        void binderDied(const wp<IBinder> &who)
        {
            ALOGW("system_write died!");
        }
};

static sp<ISystemControlService> amSystemControlService;
static sp<DeathNotifier> amDeathNotifier;
static  Mutex            amLock;
static  Mutex            amgLock;

const sp<ISystemControlService> &getSystemControlService()
{
    Mutex::Autolock _l(amgLock);

    if (amSystemControlService.get() == 0)
    {
        sp<IServiceManager> sm = defaultServiceManager();
        sp<IBinder> binder;

        do
        {
            binder = sm->getService(String16("system_control"));

            if (binder != 0)
                break;

            ALOGW("SystemControl not published, waiting...");
            usleep(500000); // 0.5 s
        }
        while (true);

        if (amDeathNotifier == NULL)
        {
            amDeathNotifier = new DeathNotifier();
        }

        binder->linkToDeath(amDeathNotifier);
        amSystemControlService = interface_cast<ISystemControlService>(binder);
    }

    ALOGE_IF(amSystemControlService == 0, "no System Control Service!?");
    return amSystemControlService;
}

int amSystemControlGetProperty(const char *key, char *value)
{
    const sp<ISystemControlService> &scs = getSystemControlService();

    if (scs != 0)
    {
        String16 v;

        if (scs->getProperty(String16(key), v))
        {
            strcpy(value, String8(v).string());
            return 0;
        }
    }

    return -1;
}

int amSystemControlGetPropertyStr(const char *key, char *def, char *value)
{
    const sp<ISystemControlService> &scs = getSystemControlService();

    if (scs != 0)
    {
        String16 v;
        String16 d(def);
        scs->getPropertyString(String16(key), d, v);
        strcpy(value, String8(v).string());
    }

    strcpy(value, def);
    return -1;
}

int amSystemControlGetPropertyInt(const char *key, int def)
{
    const sp<ISystemControlService> &scs = getSystemControlService();

    if (scs != 0)
    {
        return scs->getPropertyInt(String16(key), def);
    }

    return def;
}


long amSystemControlGetPropertyLong(const char *key, long def)
{
    const sp<ISystemControlService> &scs = getSystemControlService();

    if (scs != 0)
    {
        return  scs->getPropertyLong(String16(key), def);
    }

    return def;
}


int amSystemControlGetPropertyBool(const char *key, int def)
{
    const sp<ISystemControlService> &scs = getSystemControlService();

    if (scs != 0)
    {
        if (scs->getPropertyBoolean(String16(key), def))
        {
            return 1;
        }
        else
        {
            return 0;
        }
    }

    return def;
}

void amSystemControlSetProperty(const char *key, const char *value)
{
    const sp<ISystemControlService> &scs = getSystemControlService();

    if (scs != 0)
    {
        scs->setProperty(String16(key), String16(value));
    }
}

int amSystemControlReadSysfs(const char *path, char *value)
{
    //ALOGD("amSystemControlReadNumSysfs:%s",path);
    const sp<ISystemControlService> &scs = getSystemControlService();

    if (scs != 0)
    {
        String16 v;

        if (scs->readSysfs(String16(path), v))
        {
            strcpy(value, String8(v).string());
            return 0;
        }
    }

    return -1;
}

int amSystemControlReadNumSysfs(const char *path, char *value, int size)
{
    //ALOGD("amSystemControlReadNumSysfs:%s",path);
    const sp<ISystemControlService> &scs = getSystemControlService();

    if (scs != 0 && value != NULL && access(path, 0) != -1)
    {
        String16 v;

        if (scs->readSysfs(String16(path), v))
        {
            if (v.size() != 0)
            {
                //ALOGD("readSysfs ok:%s,%s,%d", path, String8(v).string(), String8(v).size());
                memset(value, 0, size);

                if (size <= String8(v).size() + 1)
                {
                    memcpy(value, String8(v).string(), size - 1);
                    value[strlen(value)] = '\0';
                }
                else
                {
                    strcpy(value, String8(v).string());
                }

                return 0;
            }
        }
    }

    //ALOGD("[false]amSystemControlReadNumSysfs%s,",path);
    return -1;
}

int amSystemControlWriteSysfs(const char *path, char *value)
{
    //ALOGD("amSystemControlWriteSysfs:%s",path);
    const sp<ISystemControlService> &scs = getSystemControlService();

    if (scs != 0)
    {
        String16 v(value);

        if (scs->writeSysfs(String16(path), v))
        {
            //ALOGD("writeSysfs ok");
            return 0;
        }
    }

    //ALOGD("[false]amSystemControlWriteSysfs%s,",path);
    return -1;
}
void amDumpMemoryAddresses(int fd) {
    ALOGE("[amDumpMemoryAddresses]fd:%d\n",fd);
    dumpMemoryAddresses(fd);
    close(fd);
}

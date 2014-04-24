/****************************************
 * file: sub_set_sys.c
 * description: set sys attr when
****************************************/
#include <stdio.h>
#include <unistd.h>
#include <stdlib.h>
#include <fcntl.h>
#include <string.h>
#include <log_print.h>
#include "linux/ioctl.h"
#include "amstream.h"
#include <Amsysfsutils.h>

#define CODEC_AMSUBTITLE_DEVICE     "/dev/amsubtitle"

#define msleep(n)	usleep(n*1000)

int set_sysfs_int(const char *path, int val)
{
    return amsysfs_set_sysfs_int(path, val);
}
int get_sysfs_int(const char *path)
{
    return amsysfs_get_sysfs_int(path);
}

int set_subtitle_enable(int enable)
{
	log_print("[%s::%d] %d,----- \n",__FUNCTION__,__LINE__, enable);
    return set_sysfs_int("/sys/class/subtitle/enable", enable);
}

int get_subtitle_enable()
{
	log_print("[%s::%d] %d,------ \n",__FUNCTION__,__LINE__, get_sysfs_int("/sys/class/subtitle/enable"));
    return get_sysfs_int("/sys/class/subtitle/enable");
}

int get_subtitle_num()
{
    return get_sysfs_int("/sys/class/subtitle/total");
}

int set_subtitle_curr(int curr)
{
    return set_sysfs_int("/sys/class/subtitle/curr", curr);
}

int get_subtitle_curr()
{
    return get_sysfs_int("/sys/class/subtitle/curr");
}

int set_subtitle_size(int size)
{
    return set_sysfs_int("/sys/class/subtitle/size", size);
}

int get_subtitle_size()
{
    return get_sysfs_int("/sys/class/subtitle/size");
}

int set_subtitle_data(int data)
{
    return set_sysfs_int("/sys/class/subtitle/data", data);
}

int get_subtitle_data()
{
    return get_sysfs_int("/sys/class/subtitle/data");
}

int get_subtitle_startpts()
{
    return get_sysfs_int("/sys/class/subtitle/startpts");
}

int get_subtitle_fps()
{
    return get_sysfs_int("/sys/class/subtitle/fps");
}

int get_subtitle_subtype()
{
    return get_sysfs_int("/sys/class/subtitle/subtype");
}

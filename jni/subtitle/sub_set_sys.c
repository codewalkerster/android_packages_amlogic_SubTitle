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

#define CODEC_AMSUBTITLE_DEVICE     "/dev/amsubtitle"

typedef enum {
    SUB_NULL = -1,
    SUB_ENABLE = 0,
    SUB_TOTAL,
    SUB_WIDTH,
    SUB_HEIGHT,
    SUB_TYPE,
    SUB_CURRENT,
    SUB_INDEX,
    SUB_WRITE_POS,
    SUB_START_PTS,
    SUB_FPS,
    SUB_SUBTYPE,
    SUB_RESET,
    SUB_DATA_T_SIZE,
    SUB_DATA_T_DATA
}subinfo_para_type;

typedef struct {
    subinfo_para_type subinfo_type;
    int subtitle_info;
    char *data;
} subinfo_para_t;

#define msleep(n)	usleep(n*1000)

int codec_h_open(const char *port_addr, int flags)
{
    int r;
	int retry_open_times=0;
retry_open:
    r = open(port_addr, flags);
    if (r<0 /*&& r==EBUSY*/) {
	    retry_open_times++;
	    if(retry_open_times==1)
          log_print("Init [%s] failed,ret = %d retry_open!\n", port_addr, r);
	    msleep(10);
	    if(retry_open_times<1000)
	       goto retry_open;
	    log_print("retry_open [%s] failed,ret = %d used_times=%d*10(ms)\n", port_addr,r,retry_open_times);
		
        return r;
    }
	if(retry_open_times>0)
		log_print("retry_open [%s] success,ret = %d used_times=%d*10(ms)\n", port_addr,r,retry_open_times);
    return r;
}

int codec_h_close(int h)
{
	int r;
    if (h >= 0) {
        r = close(h);
		if (r < 0) {
        	log_print("close failed,handle=%d,ret=%d \n", h, r);
    	}
    }
    return 0;
}


int set_subtitle_info(subinfo_para_t sub_info)
{
    int utils_fd, ret;

    utils_fd = codec_h_open(CODEC_AMSUBTITLE_DEVICE, O_RDWR);
    if (utils_fd < 0) {
        log_print("[%s::%d] open_amsubtitle failed! \n",__FUNCTION__,__LINE__);
        return -1;
    }
	
    ret = ioctl(utils_fd, AMSTREAM_IOC_SET_SUBTITLE_INFO, (unsigned long)&sub_info);
    if (ret < 0) {
        log_print("## amsubtitle set failed -------\n");
        codec_h_close(utils_fd);
        return -1;
    }

    codec_h_close(utils_fd);

	return 0;
}

int get_subtitle_info(subinfo_para_t *sub_info)
{
    int utils_fd, ret;

    utils_fd = codec_h_open(CODEC_AMSUBTITLE_DEVICE, O_RDWR);
    if (utils_fd < 0) {
        log_print("[%s::%d] open_amsubtitle failed! \n",__FUNCTION__,__LINE__);
        return -1;
    }
	
    ret = ioctl(utils_fd, AMSTREAM_IOC_GET_SUBTITLE_INFO, (unsigned long)sub_info);
    if (ret < 0) {
        log_print("## amsubtitle set failed -------\n");
        codec_h_close(utils_fd);
        return -1;
    }

    codec_h_close(utils_fd);

	return 0;
}

int set_subtitle_enable(int enable)
{
    subinfo_para_t sub_info;
    int ret;

    sub_info.subinfo_type = SUB_ENABLE;
    sub_info.subtitle_info = enable;
    log_print("## set_subtitle_enable %d, ----\n", enable);

    ret = set_subtitle_info(sub_info);
    if (ret<0) {
        log_print("[%s::%d] set_subtitle_num failed! \n",__FUNCTION__,__LINE__);
        return -1;
    }

    return 0;
}

int get_subtitle_enable()
{
    subinfo_para_t sub_info;
    int ret;

    sub_info.subinfo_type = SUB_ENABLE;

    ret = get_subtitle_info(&sub_info);
    if (ret<0) {
        log_print("[%s::%d] set_subtitle_num failed! \n",__FUNCTION__,__LINE__);
        return -1;
    }

    return sub_info.subtitle_info;
}

int get_subtitle_num()
{
    subinfo_para_t sub_info;
    int ret;

    sub_info.subinfo_type = SUB_TOTAL;

    ret = get_subtitle_info(&sub_info);
    if (ret<0) {
        log_print("[%s::%d] set_subtitle_num failed! \n",__FUNCTION__,__LINE__);
        return -1;
    }

    return sub_info.subtitle_info;
}

int set_subtitle_curr(int curr)
{
    subinfo_para_t sub_info;
    int ret;

    sub_info.subinfo_type = SUB_CURRENT;
    sub_info.subtitle_info = curr;

    ret = set_subtitle_info(sub_info);
    if (ret<0) {
        log_print("[%s::%d] set_subtitle_num failed! \n",__FUNCTION__,__LINE__);
        return -1;
    }

    return 0;
}

int get_subtitle_curr()
{
    subinfo_para_t sub_info;
    int ret;

    sub_info.subinfo_type = SUB_CURRENT;

    ret = get_subtitle_info(&sub_info);
    if (ret<0) {
        log_print("[%s::%d] set_subtitle_num failed! \n",__FUNCTION__,__LINE__);
        return -1;
    }

    return sub_info.subtitle_info;
}

int set_subtitle_size(int size)
{
    subinfo_para_t sub_info;
    int ret;

    sub_info.subinfo_type = SUB_DATA_T_SIZE;
    sub_info.subtitle_info = size;

    ret = set_subtitle_info(sub_info);
    if (ret<0) {
        log_print("[%s::%d] set_subtitle_num failed! \n",__FUNCTION__,__LINE__);
        return -1;
    }

    return 0;
}

int get_subtitle_size()
{
    subinfo_para_t sub_info;
    int ret;

    sub_info.subinfo_type = SUB_DATA_T_SIZE;

    ret = get_subtitle_info(&sub_info);
    if (ret<0) {
        log_print("[%s::%d] set_subtitle_num failed! \n",__FUNCTION__,__LINE__);
        return -1;
    }

    return sub_info.subtitle_info;
}

int set_subtitle_data(int data)
{
    subinfo_para_t sub_info;
    int ret;

    sub_info.subinfo_type = SUB_DATA_T_DATA;
    sub_info.subtitle_info = data;

    ret = set_subtitle_info(sub_info);
    if (ret<0) {
        log_print("[%s::%d] set_subtitle_num failed! \n",__FUNCTION__,__LINE__);
        return -1;
    }

    return 0;
}

int get_subtitle_data()
{
    subinfo_para_t sub_info;
    int ret;

    sub_info.subinfo_type = SUB_DATA_T_DATA;

    ret = get_subtitle_info(&sub_info);
    if (ret<0) {
        log_print("[%s::%d] set_subtitle_num failed! \n",__FUNCTION__,__LINE__);
        return -1;
    }

    return sub_info.subtitle_info;
}

int get_subtitle_startpts()
{
    subinfo_para_t sub_info;
    int ret;

    sub_info.subinfo_type = SUB_START_PTS;

    ret = get_subtitle_info(&sub_info);
    if (ret<0) {
        log_print("[%s::%d] set_subtitle_num failed! \n",__FUNCTION__,__LINE__);
        return -1;
    }

    return sub_info.subtitle_info;
}

int get_subtitle_fps()
{
    subinfo_para_t sub_info;
    int ret;

    sub_info.subinfo_type = SUB_FPS;

    ret = get_subtitle_info(&sub_info);
    if (ret<0) {
        log_print("[%s::%d] set_subtitle_num failed! \n",__FUNCTION__,__LINE__);
        return -1;
    }

    return sub_info.subtitle_info;
}

int get_subtitle_subtype()
{
    subinfo_para_t sub_info;
    int ret;

    sub_info.subinfo_type = SUB_SUBTYPE;

    ret = get_subtitle_info(&sub_info);
    if (ret<0) {
        log_print("[%s::%d] set_subtitle_num failed! \n",__FUNCTION__,__LINE__);
        return -1;
    }

    return sub_info.subtitle_info;
}


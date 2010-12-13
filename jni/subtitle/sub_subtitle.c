/************************************************
 * name	:subtitle.c
 * function	:decoder relative functions
 * data		:2010.8.11
 * author		:FFT
 * version	:1.0.0
 *************************************************/
 //header file
#include <unistd.h>
#include <stdio.h>
#include <string.h>
#include <errno.h>
#include <getopt.h>
#include <fcntl.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <sys/poll.h>
#include <sys/ioctl.h>
#include <android/log.h>
#include "amstream.h"



#include "sub_subtitle.h"
#include "sub_vob_sub.h"

#define SUBTITLE_VOB      0
#define SUBTITLE_PGS      1
#define SUBTITLE_MKV_STR  2
#define SUBTITLE_MKV_VOB  3
#define  LOG_TAG    "sub_jni"
#define  LOGI(...)  __android_log_print(ANDROID_LOG_INFO,LOG_TAG,__VA_ARGS__)
#define  LOGE(...)  __android_log_print(ANDROID_LOG_ERROR,LOG_TAG,__VA_ARGS__)
#define SUBTITLE_READ_DEVICE    "/dev/amstream_sub_read"
#define SUBTITLE_FILE "/tmp/subtitle.db"
#define VOB_SUBTITLE_FRAMW_SIZE   (4+1+4+4+2+2+2+2+2+4+VOB_SUB_SIZE)
#define MAX_SUBTITLE_PACKET_WRITE	50
#define ADD_SUBTITLE_POSITION(x)  (((x+1)<MAX_SUBTITLE_PACKET_WRITE)?(x+1):0)
static off_t file_position=0;
static off_t read_position=0;
static int  aml_sub_handle = -1;
typedef struct{
	int subtitle_size;
	int subtitle_pts;
	int subtitle_delay_pts;
	int data_size;
	int subtitle_width;
	int subtitle_height;
	int resize_height;
	int resize_width;
	int resize_xstart;
	int resize_ystart;
	int resize_size;
	unsigned short sub_alpha;
	char * data;
}subtitle_data_t;
static subtitle_data_t inter_subtitle_data[MAX_SUBTITLE_PACKET_WRITE];

static unsigned short DecodeRL(unsigned short RLData,unsigned short *pixelnum,unsigned short *pixeldata)
{
	unsigned short nData = RLData;
	unsigned short nShiftNum;
	unsigned short nDecodedBits;
	
	if(nData & 0xc000) 
		nDecodedBits = 4;
	else if(nData & 0x3000) 
		nDecodedBits = 8;
	else if(nData & 0x0c00) 
		nDecodedBits = 12;
	else 
		nDecodedBits = 16;
	
	nShiftNum = 16 - nDecodedBits;
	*pixeldata = (nData >> nShiftNum) & 0x0003;
	*pixelnum = nData >> (nShiftNum + 2);
	
	return nDecodedBits;	
}

static unsigned short GetWordFromPixBuffer(unsigned short bitpos, unsigned short *pixelIn)
{
	unsigned char hi=0, lo=0, hi_=0, lo_=0;
	char *tmp = (char *)pixelIn;

	hi = *(tmp+0);
	lo = *(tmp+1);
	hi_ = *(tmp+2);
	lo_ = *(tmp+3);

	if(bitpos == 0){
		return (hi<<0x8 | lo);
	}
	else {
		return(((hi<<0x8 | lo) << bitpos) | ((hi_<<0x8 | lo_)>>(16 - bitpos)));
	}
}

unsigned char spu_fill_pixel(unsigned short *pixelIn, char *pixelOut, AML_SPUVAR *sub_frame)
{
	unsigned short nPixelNum = 0,nPixelData = 0;
	unsigned short nRLData,nBits;
	unsigned short nDecodedPixNum = 0;
	unsigned short i, j;
	unsigned short PXDBufferBitPos	= 0,WrOffset = 16;
	unsigned short change_data = 0;
    unsigned short PixelDatas[4] = {0,1,2,3};
	unsigned short rownum = sub_frame->spu_width;
	unsigned short height = sub_frame->spu_height;
	
	static unsigned short *ptrPXDWrite;
        
	memset(pixelOut, 0, VOB_SUB_SIZE/2);
	ptrPXDWrite = (unsigned short *)pixelOut;

	for (j=0; j<height/2; j++) {
		while(nDecodedPixNum < rownum){
			nRLData = GetWordFromPixBuffer(PXDBufferBitPos, pixelIn);
			nBits = DecodeRL(nRLData,&nPixelNum,&nPixelData);

			PXDBufferBitPos += nBits;
			if(PXDBufferBitPos >= 16){
				PXDBufferBitPos -= 16;
				pixelIn++;
			}
			if(nPixelNum == 0){
				nPixelNum = rownum - nDecodedPixNum%rownum;
			}
            
    		if(change_data)
    		{
                nPixelData = PixelDatas[nPixelData];
    		}
            
			for(i = 0;i < nPixelNum;i++){
				WrOffset -= 2;
				*ptrPXDWrite |= nPixelData << WrOffset;
				if(WrOffset == 0){
					WrOffset = 16;
					ptrPXDWrite++;
				}
			}
			nDecodedPixNum += nPixelNum;
		}	

		if(PXDBufferBitPos == 4) {			 //Rule 6
			PXDBufferBitPos = 8;
		}
		else if(PXDBufferBitPos == 12){
			PXDBufferBitPos = 0;
			pixelIn++;
		}
		
		if (WrOffset != 16) {
		    WrOffset = 16;
		    ptrPXDWrite++;
		}

		nDecodedPixNum -= rownum;

	}

	return 0;
}


int subtitle_poll_sub_fd(int sub_fd, int timeout)
{
    struct pollfd sub_poll_fd[1];

    if (sub_fd <= 0)
    {
        return 0;
    }
    
    sub_poll_fd[0].fd = sub_fd;
    sub_poll_fd[0].events = POLLOUT;

    return poll(sub_poll_fd, 1, timeout);    
}


int subtitle_get_sub_size_fd(int sub_fd)
{
    int sub_size, r;
    
    r=ioctl(sub_fd,AMSTREAM_IOC_SUB_LENGTH,(unsigned long)&sub_size);
    if(r<0)
        return 0;
    else
        return sub_size;
}


int subtitle_read_sub_data_fd(int sub_fd, char *buf, unsigned int length)
{
    int data_size=length, r, read_done=0;


    while (data_size)
    {
        r = read(sub_fd,buf+read_done,data_size);
        if (r<0)
            return 0;
        else
        {
            data_size -= r;
            read_done += r;
        }
    }

    return 0;
}

int get_spu(AML_SPUVAR *spu, int read_sub_fd)
{
	int ret, rd_oft, wr_oft, size;
	char *spu_buf=NULL;
	unsigned current_length, current_pts, current_type;
	if(aml_sub_handle < 0){
		aml_sub_handle = open(SUBTITLE_READ_DEVICE,O_RDONLY);
	}
	if(aml_sub_handle < 0){
		LOGI("subtitle read device open fail\n");
		return 0;
	}
	read_sub_fd = aml_sub_handle;

	if(read_sub_fd < 0)
		return 0;
	ret = subtitle_poll_sub_fd(read_sub_fd, 10);
	
	if (ret == 0){
	    //LOGI("codec_poll_sub_fd fail \n\n");
	    ret = -1;
		goto error; 
	}

	size = subtitle_get_sub_size_fd(read_sub_fd);
	if (size <= 0){
    ret = -1;
    	LOGI("\n player get sub size less than zero \n\n");
		goto error; 
	}
	else{
    	LOGI("\n malloc subtitle size %d \n\n",size);
		spu_buf = malloc(size);	
	}

	ret = subtitle_read_sub_data_fd(read_sub_fd, spu_buf, size);
	

	rd_oft = 0;
	if ((spu_buf[rd_oft++]!=0x41)||(spu_buf[rd_oft++]!=0x4d)||
		(spu_buf[rd_oft++]!=0x4c)||(spu_buf[rd_oft++]!=0x55)|| (spu_buf[rd_oft++]!=0xaa)){
		ret = -1;
		goto error; 		// wrong head
	}
	LOGI("\n\n ******* find correct subtitle header ******\n\n");
	current_type = spu_buf[rd_oft++]<<16;
	current_type |= spu_buf[rd_oft++]<<8;
	current_type |= spu_buf[rd_oft++];

	current_length = spu_buf[rd_oft++]<<24;
	current_length |= spu_buf[rd_oft++]<<16;
	current_length |= spu_buf[rd_oft++]<<8;
	current_length |= spu_buf[rd_oft++];	
	
	current_pts = spu_buf[rd_oft++]<<24;
	current_pts |= spu_buf[rd_oft++]<<16;
	current_pts |= spu_buf[rd_oft++]<<8;
	current_pts |= spu_buf[rd_oft++];
  	LOGI("current_pts is %d\n",current_pts);
	if (current_pts==0){
	ret = -1;
		goto error;
		}
  	LOGI("current_type is %d\n",current_type);
	switch (current_type) {
		case 0x17000:
      spu->subtitle_type = SUBTITLE_VOB;
			spu->spu_data = malloc(VOB_SUB_SIZE);
			spu->pts = current_pts;
			ret = get_vob_spu(spu_buf+rd_oft, current_length, spu); 
			break;

		default:
      ret = -1;
			break;
	}

error:
	if (spu_buf)
		free(spu_buf);
		
	return ret;
}


int release_spu(AML_SPUVAR *spu)
{
	if(spu->spu_data)
		free(spu->spu_data);

	return 0;
}

int set_int_value(int value, char *data, int *pos)
{
	data[0] = (value>>24)&0xff;
	data[1] = (value>>16)&0xff;
	data[2] = (value>>8 )&0xff;
	data[3] = value & 0xff;	
	*pos += 4;
	return 0;
}

int set_short_value(unsigned short value, char *data, int *pos)
{
	data[0] = (value>>8 )&0xff;
	data[1] = value & 0xff;	
	*pos += 2;
	return 0;
}

int init_subtitle_file()
{
	file_position = 0;
	return 0;
}

/*
write subtitle to file:SUBTITLE_FILE
first 4 bytes are sync bytes:0x414d4c55(AMLU)
next  1 byte  is  subtitle type
next  4 bytes are subtitle pts
mext  4 bytes arg subtitle delay
next  2 bytes are subtitle start x pos
next  2 bytes are subtitel start y pos
next  2 bytes are subtitel width
next  2 bytes are subtitel height
next  2 bytes are subtitle alpha
next  4 bytes are subtitel size
next  n bytes are subtitle data
*/
int write_subtitle_file(AML_SPUVAR *spu)
{
#if 1
	char *subtitle_data = NULL;
	subtitle_data = malloc(VOB_SUB_SIZE);
	if(subtitle_data == NULL)
		return 0;
	if(inter_subtitle_data[file_position].data)
		free(inter_subtitle_data[file_position].data);
	#if 0
	int subtitle_data_pos = 0;
	set_int_value(spu->sync_bytes, subtitle_data+subtitle_data_pos, &subtitle_data_pos);
	subtitle_data[subtitle_data_pos] = spu->subtitle_type;
	subtitle_data_pos ++;
	set_int_value(spu->pts, subtitle_data+subtitle_data_pos, &subtitle_data_pos);
	set_int_value(spu->m_delay, subtitle_data+subtitle_data_pos, &subtitle_data_pos);
	set_short_value(spu->spu_start_x, subtitle_data+subtitle_data_pos, &subtitle_data_pos);
	set_short_value(spu->spu_start_y, subtitle_data+subtitle_data_pos, &subtitle_data_pos);
	set_short_value(spu->spu_width, subtitle_data+subtitle_data_pos, &subtitle_data_pos);
	set_short_value(spu->spu_height, subtitle_data+subtitle_data_pos, &subtitle_data_pos);
	set_short_value(spu->spu_alpha, subtitle_data+subtitle_data_pos, &subtitle_data_pos);
	set_int_value(spu->buffer_size, subtitle_data+subtitle_data_pos, &subtitle_data_pos);
	memcpy(subtitle_data+subtitle_data_pos, spu->spu_data, VOB_SUB_SIZE);
	#endif
	memcpy(subtitle_data, spu->spu_data, VOB_SUB_SIZE);
	inter_subtitle_data[file_position].data = subtitle_data;
	inter_subtitle_data[file_position].data_size = VOB_SUBTITLE_FRAMW_SIZE;
	inter_subtitle_data[file_position].subtitle_pts = spu->pts;
	inter_subtitle_data[file_position].subtitle_delay_pts = spu->m_delay;
	inter_subtitle_data[file_position].sub_alpha = spu->spu_alpha;
	inter_subtitle_data[file_position].subtitle_width = spu->spu_width;
	inter_subtitle_data[file_position].subtitle_height = spu->spu_height;
#elif 0


	int subfile = open(SUBTITLE_FILE, O_RDWR|O_CREAT);
	if(subfile >= 0){
	    CODEC_PRINT("write pos is %ll\n\n",file_position*VOB_SUBTITLE_FRAMW_SIZE);
	    off_t lseek_value = lseek(subfile,file_position*VOB_SUBTITLE_FRAMW_SIZE,SEEK_SET);
	    CODEC_PRINT("lseek_value is %ll\n\n",lseek_value);
		CODEC_PRINT("open subtitle file success\n\n");
		CODEC_PRINT("sync_bytes is 0x414d4c55\n\n");
		write(subfile,&spu->sync_bytes,4);
		CODEC_PRINT("subtitle_type is %d\n\n",spu->subtitle_type);
		write(subfile,&spu->subtitle_type,1);
		CODEC_PRINT("spu->pts is %d\n\n",spu->pts);
		write(subfile,&spu->pts,4);
	    CODEC_PRINT("spu->m_delay is %d\n\n",spu->m_delay);
		write(subfile,&spu->m_delay,4);
		CODEC_PRINT("spu->spu_start_x is %d\n\n",spu->spu_start_x);
		write(subfile,&spu->spu_start_x,2);
		CODEC_PRINT("spu->spu_start_y is %d\n\n",spu->spu_start_y);
		write(subfile,&spu->spu_start_y,2);   
		CODEC_PRINT("spu->spu_width is %d\n\n",spu->spu_width);
		write(subfile,&spu->spu_width,2);
		CODEC_PRINT("spu->spu_height is %d\n\n",spu->spu_height);
		write(subfile,&spu->spu_height,2);
		CODEC_PRINT("spu->spu_alpha is %x\n\n",spu->spu_alpha);	
		write(subfile,&spu->spu_alpha,2);
		CODEC_PRINT("VOB_SUB_SIZE is %d\n\n",VOB_SUB_SIZE);		
		write(subfile,&spu->buffer_size,4);
		write(subfile,spu->spu_data,VOB_SUB_SIZE);
		CODEC_PRINT("write subtitle file success\n\n");
		close(subfile);
	}
#endif
	return 0;
}

int read_subtitle_file()
{
#if 1
	LOGI("subtitle data address is %x\n\n",(int)inter_subtitle_data[file_position].data);
#elif 0
	int subfile = open(SUBTITLE_FILE, O_RDONLY);
	if(subfile >= 0){
		unsigned sync_byte = 0;
		unsigned short spu_rect = 0;
		CODEC_PRINT("read pos is %ll\n\n",file_position*VOB_SUBTITLE_FRAMW_SIZE);
		lseek(subfile,file_position*VOB_SUBTITLE_FRAMW_SIZE,SEEK_SET);
		read(subfile, &sync_byte, 4);
		CODEC_PRINT("sync bytes is %x\n\n",sync_byte); 
		unsigned char subtitle_type = 0;
    	read(subfile, &subtitle_type, 1);
		CODEC_PRINT("subtitle_type is %x\n\n",subtitle_type); 
		sync_byte = 0;
		read(subfile, &sync_byte, 4);
		CODEC_PRINT("current pts is %d\n\n",sync_byte); 
		sync_byte = 0;
		read(subfile, &sync_byte, 4);
		CODEC_PRINT("subtitel delay is %d\n\n",sync_byte);
		read(subfile, &spu_rect, 2);
		CODEC_PRINT("spu_start_x is %d\n\n",spu_rect); 
		spu_rect = 0;
		read(subfile, &spu_rect, 2);
		CODEC_PRINT("spu_start_y is %d\n\n",spu_rect); 
		spu_rect = 0;
		read(subfile, &spu_rect, 2);
		CODEC_PRINT("spu_width is %d\n\n",spu_rect); 
		spu_rect = 0;
		read(subfile, &spu_rect, 2);
		CODEC_PRINT("spu_height is %d\n\n",spu_rect); 
		spu_rect = 0;
		read(subfile, &spu_rect, 2);
		CODEC_PRINT("spu_alpha is %x\n\n",spu_rect);
		sync_byte = 0;
		read(subfile, &sync_byte, 4);
		CODEC_PRINT("spu size is %d\n\n",sync_byte);  
		close(subfile);

	}
#endif
	return 0;
}

int get_inter_spu_packet(int pts)
{
	if(inter_subtitle_data[read_position].subtitle_pts > pts)
		return -1;
	#if 0
	for(int i=0; i<MAX_SUBTITLE_PACKET_WRITE; i++){
		if((inter_subtitle_data[read_position].subtitle_pts <= pts) && 
			(inter_subtitle_data[read_position].subtitle_delay_pts > pts))
			break;
		else
			ADD_SUBTITLE_POSITION(read_position);
		read_position++;
	}
	#endif
	LOGI("read_position is %d\n",read_position);
	LOGI("file_position is %d\n",file_position);
	return read_position;
}

int get_inter_spu_size()
{
	int subtitle_width = inter_subtitle_data[read_position].subtitle_width;
	int subtitle_height = inter_subtitle_data[read_position].subtitle_height;
	if(subtitle_width * subtitle_height == 0)
		return 0;
	int buffer_width = (subtitle_width+63)&0xffffffc0;
	LOGI("buffer width is %d\n",buffer_width);
	LOGI("buffer height is %d\n",subtitle_height);
	return buffer_width*subtitle_height;
}

int get_inter_spu_width()
{
	return inter_subtitle_data[read_position].resize_width;
	//return ((inter_subtitle_data[read_position].subtitle_width+63)&0xffffffc0);
}

int get_inter_spu_height()
{
	return inter_subtitle_data[read_position].resize_height;
	//return inter_subtitle_data[read_position].subtitle_height;
}


int get_inter_spu_delay()
{
	return inter_subtitle_data[read_position].subtitle_delay_pts;
}

int get_inter_spu_resize_size()
{
	return inter_subtitle_data[read_position].resize_size;
}

int add_read_position()
{
	read_position = ADD_SUBTITLE_POSITION(read_position);
	LOGI("read_position is %d\n\n",read_position);
	return 0;
}

int fill_resize_data(int *dst_data, int *src_data)
{
	int y_start = inter_subtitle_data[read_position].resize_ystart;
	int x_start = inter_subtitle_data[read_position].resize_xstart;
	int y_end = y_start+inter_subtitle_data[read_position].resize_height;
	int resize_width = inter_subtitle_data[read_position].resize_width;
	int buffer_width = inter_subtitle_data[read_position].subtitle_width;
	int buffer_height = inter_subtitle_data[read_position].subtitle_height;
	int buffer_width_size = (buffer_width+63)&0xffffffc0;
	int *resize_src_data = src_data + buffer_width_size*y_start;
	int i = y_start;
	for(; i<y_end; i++){
		memcpy(dst_data+(resize_width*(i-y_start)), 
			resize_src_data+(buffer_width_size*(i-y_start))+x_start,
			resize_width*4);		
	}
	return 0;
	
}

int *parser_inter_spu(int *buffer)
{
	LOGI("enter parser_inter_sup \n\n");
	
	unsigned short i=0,j=0;
	unsigned char *data = NULL, *data2 = NULL;
	unsigned char color = 0;
	unsigned *result_buf = (unsigned *)buffer;
    unsigned index = 0, index1 = 0;
	unsigned char n = 0;
	unsigned short buffer_width, buffer_height;
	int start_height = -1, end_height = 0;
	buffer_width = inter_subtitle_data[read_position].subtitle_width;
	buffer_height = inter_subtitle_data[read_position].subtitle_height;
	int resize_width = buffer_width, resize_height;
	int x_start = buffer_width, x_end = 0;
    unsigned data_byte = (((buffer_width*2)+15)>>4)<<1;
	LOGI("data_byte is %d\n\n",data_byte);
	int buffer_width_size = (buffer_width+63)&0xffffffc0;
	LOGI("buffer_width is %d\n\n",buffer_width_size);
	unsigned short subtitle_alpha = inter_subtitle_data[read_position].sub_alpha;
	LOGI("subtitle_alpha is %x\n\n",subtitle_alpha);
	unsigned int RGBA_Pal[4];
	RGBA_Pal[0] = RGBA_Pal[1] = RGBA_Pal[2] = RGBA_Pal[3] = 0;
	if((subtitle_alpha==0xff0))
    {
        RGBA_Pal[2] = 0xffffffff;
		RGBA_Pal[1] = 0xff0000ff; 
    }
    else if((subtitle_alpha==0xfff0)){
        RGBA_Pal[1] = 0xffffffff;
		RGBA_Pal[2] = 0xff000000; 
		RGBA_Pal[3] = 0xff000000;
    }
    else if((subtitle_alpha==0xf0f0)){
        RGBA_Pal[1] = 0xffffffff;
		RGBA_Pal[3] = 0xff000000;
    }
    else if((subtitle_alpha==0xff00)){
		RGBA_Pal[2] = 0xffffffff; 
		RGBA_Pal[3] = 0xff000000;
    }
	else{
		RGBA_Pal[1] = 0xffffffff;
		RGBA_Pal[3] = 0xff000000;
	}
    for (i=0;i<buffer_height;i++){
		if(i&1)
			data = inter_subtitle_data[read_position].data+(i>>1)*data_byte + (720*576/8);
		else
			data = inter_subtitle_data[read_position].data+(i>>1)*data_byte;
		index=0;
		for (j=0;j<buffer_width;j++){
			index1 = index%2?index-1:index+1;
			n = data[index1];
			index++;
			if(n){
				if(start_height < 0){
					start_height = i;
					//start_height = (start_height%2)?(start_height-1):start_height;
				}
				end_height = i;
				if(j < x_start)
					x_start = j;
	            result_buf[i*(buffer_width_size)+j] = RGBA_Pal[(n>>6)&0x3];
	            if(++j >= buffer_width)    break;
	            result_buf[i*(buffer_width_size)+j] = RGBA_Pal[(n>>4)&0x3];
	            if(++j >= buffer_width)    break;
	            result_buf[i*(buffer_width_size)+j] = RGBA_Pal[(n>>2)&0x3];
	            if(++j >= buffer_width)    break;
	            result_buf[i*(buffer_width_size)+j] = RGBA_Pal[n&0x3];
				if(j > x_end)
					x_end = j;
			}
			else
				j+=3;
			
		}
		
	}
	//end_height = (end_height%2)?(((end_height+1)<=buffer_height)?(end_height+1):end_height):end_height;
	inter_subtitle_data[read_position].resize_xstart = x_start;
	inter_subtitle_data[read_position].resize_ystart = start_height;
	inter_subtitle_data[read_position].resize_width = (x_end - x_start + 1 + 63)&0xffffffc0;
	inter_subtitle_data[read_position].resize_height = end_height - start_height + 1;
	inter_subtitle_data[read_position].resize_size = inter_subtitle_data[read_position].resize_height * \
							inter_subtitle_data[read_position].resize_width;
	LOGI("resize height is %d\n\n",inter_subtitle_data[read_position].resize_height);
	LOGI("resize_width is %d\n\n",inter_subtitle_data[read_position].resize_width);
	return (result_buf+start_height*buffer_width_size);
	//ADD_SUBTITLE_POSITION(read_position);
	return NULL;
}

int get_inter_spu()
{  
	int read_sub_fd=0;
	AML_SPUVAR spu;
	memset(&spu,0x0,sizeof(AML_SPUVAR));
	spu.sync_bytes = 0x414d4c55;
	spu.buffer_size = VOB_SUB_SIZE;
	int ret = get_spu(&spu, read_sub_fd); 
	if(ret < 0)
		return -1;


	write_subtitle_file(&spu);
	read_subtitle_file();
	file_position = ADD_SUBTITLE_POSITION(file_position);
	LOGI("file_position is %d\n\n",file_position);

	LOGI("end parser subtitle success\n");

	return 0;
}

int close_subtitle()
{
	int i=0;
	for(i=0; i<MAX_SUBTITLE_PACKET_WRITE; i++){
		if(inter_subtitle_data[i].data)
			free(inter_subtitle_data[i].data);
		inter_subtitle_data[i].data = NULL;
		memset(&(inter_subtitle_data[i]), 0x0, sizeof(subtitle_data_t));
	}
	file_position = 0;
	read_position = 0;
	return 0;
}

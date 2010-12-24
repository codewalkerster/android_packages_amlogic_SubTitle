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


#include "sub_control.h"
#include "sub_subtitle.h"
#include "sub_vob_sub.h"
#include "sub_pgs_sub.h"

typedef struct _DivXSubPictColor
{
	char red;
	char green;
	char blue;
} DivXSubPictColor;

typedef struct _DivXSubPictHdr
{
	char duration[27];
	unsigned short width;
	unsigned short height;
	unsigned short left;
	unsigned short top;
	unsigned short right;
	unsigned short bottom;
	unsigned short field_offset;
	DivXSubPictColor background;
	DivXSubPictColor pattern;
	DivXSubPictColor emphasis1;
	DivXSubPictColor emphasis2;
	char *rleData;
} DivXSubPictHdr;

#define  LOG_TAG    "sub_subtitle"
#define  LOGI(...)  __android_log_print(ANDROID_LOG_INFO,LOG_TAG,__VA_ARGS__)
#define  LOGE(...)  __android_log_print(ANDROID_LOG_ERROR,LOG_TAG,__VA_ARGS__)
#define SUBTITLE_READ_DEVICE    "/dev/amstream_sub_read"
#define SUBTITLE_FILE "/tmp/subtitle.db"
#define VOB_SUBTITLE_FRAMW_SIZE   (4+1+4+4+2+2+2+2+2+4+VOB_SUB_SIZE)
#define MAX_SUBTITLE_PACKET_WRITE	50
#define ADD_SUBTITLE_POSITION(x)  (((x+1)<MAX_SUBTITLE_PACKET_WRITE)?(x+1):0)
#define DEC_SUBTITLE_POSITION(x)  (((x-1)>=0)?(x-1):(MAX_SUBTITLE_PACKET_WRITE-1))
static off_t file_position=0;
static off_t read_position=0;
static int  aml_sub_handle = -1;
typedef struct{
	int subtitle_type;        //add yjf 
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

unsigned char spu_fill_pixel(unsigned short *pixelIn, char *pixelOut, AML_SPUVAR *sub_frame, int n)
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
	unsigned short _alpha = sub_frame->spu_alpha;
	
	static unsigned short *ptrPXDWrite;
        
	memset(pixelOut, 0, VOB_SUB_SIZE/2);
	ptrPXDWrite = (unsigned short *)pixelOut;

	if(_alpha&0xF)
    {
        _alpha = _alpha>>4;
        change_data++;
      
        while(_alpha&0xF)
        {
           change_data++;
          _alpha = _alpha>>4;
        }

        PixelDatas[0] = change_data;
        PixelDatas[change_data] = 0;
        
        if (n==2)
          sub_frame->spu_alpha= (sub_frame->spu_alpha&0xFFF0) | (0x000F<<(change_data<<2));
    }

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

int get_spu(AML_SPUVAR *spu, int read_sub_fd)
{
	int ret, rd_oft, wr_oft, size;
	char *spu_buf=NULL;
	unsigned current_length, current_pts, current_type,duration_pts;
	DivXSubPictHdr* avihandle=NULL;
	if(read_sub_fd < 0)
		return 0;
	ret = subtitle_poll_sub_fd(read_sub_fd, 10);
	
	if (ret == 0){
	    //LOGI("codec_poll_sub_fd fail \n\n");
	    ret = -1;
		goto error; 
	}

	//if don't enable subtitle, throw subtitle data
	if(get_subtitle_enable()==0){
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
		ret = -1;
		goto error;
	}
	
	if(get_subtitle_subtype() == 1){
		//pgs subtitle
		subtitlepgs_t subtitle_pgs;
		memset(&subtitle_pgs, 0x0, sizeof(subtitlepgs_t));
		subtitle_pgs.pgs_info = malloc(sizeof(pgs_info_t));
		if(subtitle_pgs.pgs_info == NULL){
			LOGE("malloc pgs_info failed\n");
			return -1;
		}
		memset(subtitle_pgs.pgs_info, 0x0, sizeof(pgs_info_t));
		int ret_spu = get_pgs_spu(&subtitle_pgs,read_sub_fd);
		if(ret_spu == 1){	
			LOGI("sucess get_pgs_spu\n");
			spu->subtitle_type = SUBTITLE_PGS;
			spu->spu_data = subtitle_pgs.showdata.result_buf;
			spu->spu_width = subtitle_pgs.showdata.image_width;
			spu->spu_height = subtitle_pgs.showdata.image_height;
			spu->pts = subtitle_pgs.showdata.pts;
			return ret_spu;
		}
		return -1;
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
	//LOGI("current_length is %d\n",current_length);
	if (current_pts==0){
		ret = -1;
		goto error;
		}
  	LOGI("current_type is 0x%x\n",current_type);
	switch (current_type) {
		case 0x17003://avi internel image
/*			duration_pts = spu_buf[rd_oft++]<<24;
			duration_pts |= spu_buf[rd_oft++]<<16;
			duration_pts |= spu_buf[rd_oft++]<<8;
			duration_pts |= spu_buf[rd_oft++];
			LOGI("duration_pts is %d\n",duration_pts);
			
			avihandle=(DivXSubPictHdr*)(spu_buf+rd_oft);
	
			LOGI("0x17003 WIDTH %u ,HEIGHT %u\n",((avihandle->width)>>8&0xFF)|(avihandle->width<<8)&0xFF00,((avihandle->height)>>8&0xFF)|(avihandle->height<<8)&0xFF00);		
			LOGI("0x17003 left 0x%x ,top 0x%x\n",avihandle->left,avihandle->top);		
			if(avihandle->rleData=0)
			{
				ret=-1;
				break;
			}
			spu->spu_data = malloc(VOB_SUB_SIZE);

			memcpy( spu->spu_data, avihandle->rleData, VOB_SUB_SIZE );
     		spu->subtitle_type = SUBTITLE_VOB;
     		spu->buffer_size  = VOB_SUB_SIZE;
			spu->pts = current_pts;		
			ret = 0;*/
			ret=-1;
			break;
		case 0x1700a://mkv internel image
			duration_pts = spu_buf[rd_oft++]<<24;
			duration_pts |= spu_buf[rd_oft++]<<16;
			duration_pts |= spu_buf[rd_oft++]<<8;
			duration_pts |= spu_buf[rd_oft++];
			LOGI("duration_pts is %d\n",duration_pts);
		case 0x17000://vob,mkv internel image
     		spu->subtitle_type = SUBTITLE_VOB;
     		spu->buffer_size  = VOB_SUB_SIZE;
			spu->spu_data = malloc(VOB_SUB_SIZE);
			spu->pts = current_pts;
			ret = get_vob_spu(spu_buf+rd_oft, current_length, spu); 
			break;
		case 0x17002://mkv internel utf-8
		case 0x17004://mkv internel ssa
			duration_pts = spu_buf[rd_oft++]<<24;
			duration_pts |= spu_buf[rd_oft++]<<16;
			duration_pts |= spu_buf[rd_oft++]<<8;
			duration_pts |= spu_buf[rd_oft++];
			LOGI("duration_pts is %d\n",duration_pts);

			
      		spu->subtitle_type = SUBTITLE_SSA;
      		spu->buffer_size = current_length+1;//256*(current_length/256+1);
			spu->spu_data = malloc( spu->buffer_size );
			memset(spu->spu_data,'\0',sizeof(spu->buffer_size));
			spu->pts = current_pts;
			spu->m_delay = duration_pts;
			memcpy( spu->spu_data,spu_buf+rd_oft, current_length );

			LOGI("CODEC_ID_SSA   size is:    %u ,data is:    %s\n",spu->buffer_size,spu->spu_data);
			ret = 0;
			break;
		case 0x17003://avi internel image
			ret = -1;
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

int add_pgs_end_time(int end_time)
{
	if(file_position >= 0 && inter_subtitle_data[file_position].data)
		inter_subtitle_data[file_position].subtitle_delay_pts = end_time;
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
	char *subtitle_data = NULL;
	subtitle_data = malloc(spu->buffer_size);
	if(subtitle_data == NULL)
		return 0;
	if( spu->pts < inter_subtitle_data[DEC_SUBTITLE_POSITION(file_position)].subtitle_pts )
	{
		close_subtitle();
	}	
	if(inter_subtitle_data[file_position].data)
		free(inter_subtitle_data[file_position].data);

	memcpy(subtitle_data, spu->spu_data, spu->buffer_size);
	inter_subtitle_data[file_position].subtitle_type = spu->subtitle_type;
	inter_subtitle_data[file_position].data = subtitle_data;
//	inter_subtitle_data[file_position].data_size = VOB_SUBTITLE_FRAMW_SIZE;    //?? need change for text sub
	inter_subtitle_data[file_position].data_size = spu->buffer_size;    //?? need change for text sub
	inter_subtitle_data[file_position].subtitle_pts = spu->pts;
	inter_subtitle_data[file_position].subtitle_delay_pts = spu->m_delay;
	inter_subtitle_data[file_position].sub_alpha = spu->spu_alpha;
	inter_subtitle_data[file_position].subtitle_width = spu->spu_width;
	inter_subtitle_data[file_position].subtitle_height = spu->spu_height;
	inter_subtitle_data[file_position].resize_width = spu->spu_width;
	inter_subtitle_data[file_position].resize_height = spu->spu_height;
	
	
	LOGI(" write_subtitle_file[%d] subtitle_type is 0x%x size: %d \n",file_position,inter_subtitle_data[read_position].subtitle_type,
					inter_subtitle_data[file_position].data_size);
	return 0;
}

int read_subtitle_file()
{
	LOGI("subtitle data address is %x\n\n",(int)inter_subtitle_data[file_position].data);
	return 0;
}

int get_inter_spu_packet(int pts)
{
//	LOGI(" search pts %d , s %d \n",pts,pts/90);
//	LOGI("inter_subtitle_data[%d].subtitle_pts is %d\n",read_position,inter_subtitle_data[read_position].subtitle_pts);

	if(inter_subtitle_data[read_position].subtitle_pts > pts ||
		inter_subtitle_data[read_position].subtitle_pts < (pts - 10*90000))
		return -1;

	LOGI("get_inter_spu_packet  read_position is %d  file_position is %d\n",read_position,file_position);
	return read_position;
	}

int get_inter_spu_type()
{
	LOGI(" inter_subtitle_data[%d] subtitle_type is 0x%x\n",read_position,inter_subtitle_data[read_position].subtitle_type);
	return inter_subtitle_data[read_position].subtitle_type;
}

int get_inter_spu_size()
{
	if(get_inter_spu_type()==SUBTITLE_VOB)
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
	else if(get_inter_spu_type()==SUBTITLE_SSA)
	{
		LOGI(" inter_subtitle_data[%d] data_size is 0x%x\n",read_position,inter_subtitle_data[read_position].data_size);
		return inter_subtitle_data[read_position].data_size;
	}
	else if(get_inter_spu_type()==SUBTITLE_PGS)
	{
		LOGI(" inter_subtitle_data[%d] data_size is 0x%x\n",read_position,inter_subtitle_data[read_position].data_size);
		return inter_subtitle_data[read_position].data_size/4;
	}
	return 0;
}

char* get_inter_spu_data()
{
	return inter_subtitle_data[read_position].data;
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
	if(inter_subtitle_data[read_position].resize_size == get_inter_spu_size()){
		memcpy(dst_data, src_data, inter_subtitle_data[read_position].resize_size*4);
		return 0;
	}
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
	//LOGI("data_byte is %d\n\n",data_byte);
	int buffer_width_size = (buffer_width+63)&0xffffffc0;
	//LOGI("buffer_width is %d\n\n",buffer_width_size);
	unsigned short subtitle_alpha = inter_subtitle_data[read_position].sub_alpha;
	LOGI("subtitle_alpha is %x\n\n",subtitle_alpha);
	unsigned int RGBA_Pal[4];
	RGBA_Pal[0] = RGBA_Pal[1] = RGBA_Pal[2] = RGBA_Pal[3] = 0;
	#if 0
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
	else if(subtitle_alpha == 0xf0ff){
        RGBA_Pal[1] = 0xffffffff;
		RGBA_Pal[2] = 0xff000000; 
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
	#else
	if(subtitle_alpha&0xf000 && subtitle_alpha&0x0f00 &&\
		subtitle_alpha&0x00f0){
        RGBA_Pal[1] = 0xffffffff;
		RGBA_Pal[2] = 0xff000000; 
		RGBA_Pal[3] = 0xff000000;
    }
	else{
		RGBA_Pal[1] = 0xffffffff;
		RGBA_Pal[3] = 0xff000000;
	}
	#endif
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
	LOGI("resize startx is %d\n\n",inter_subtitle_data[read_position].resize_xstart);
	LOGI("resize starty is %d\n\n",inter_subtitle_data[read_position].resize_ystart);
	LOGI("resize height is %d\n\n",inter_subtitle_data[read_position].resize_height);
	LOGI("resize_width is %d\n\n",inter_subtitle_data[read_position].resize_width);
	return (result_buf+start_height*buffer_width_size);
	//ADD_SUBTITLE_POSITION(read_position);
	return NULL;
}

int get_inter_spu()
{  
	if(aml_sub_handle < 0){
		aml_sub_handle = open(SUBTITLE_READ_DEVICE,O_RDONLY);
	}
	if(aml_sub_handle < 0){
		LOGI("subtitle read device open fail\n");
		return 0;
	}
	int read_sub_fd=0;
	AML_SPUVAR spu;
	memset(&spu,0x0,sizeof(AML_SPUVAR));
	spu.sync_bytes = 0x414d4c55;
	int ret = get_spu(&spu, aml_sub_handle); 
	if(ret < 0)
		return -1;
	if(get_subtitle_subtype()==1){
		spu.buffer_size = spu.spu_width*spu.spu_height*4;
	}

	write_subtitle_file(&spu);
	//read_subtitle_file();
	free(spu.spu_data);
	file_position = ADD_SUBTITLE_POSITION(file_position);
	LOGI("file_position is %d\n\n",file_position);

	LOGI("end parser subtitle success\n");

	return 0;
}

int close_subtitle()
{
	LOGI("----------------------close_subtitle------------------------------");

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

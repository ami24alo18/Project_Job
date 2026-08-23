package com.amit.jobagent.document;
import com.amit.jobagent.common.config.JobAgentProperties;
import com.amit.jobagent.common.error.StorageUnavailableException;
import io.minio.*;
import java.io.ByteArrayInputStream;
import org.springframework.stereotype.Component;
@Component
class MinioObjectStorage implements ObjectStorage {
 private final MinioClient client;private final String bucket;
 MinioObjectStorage(MinioClient client,JobAgentProperties properties){this.client=client;this.bucket=properties.minio().bucket();}
 @Override public void store(String key,byte[] content,String type){try{ensureBucket();client.putObject(PutObjectArgs.builder().bucket(bucket).object(key).stream(new ByteArrayInputStream(content),content.length,-1).contentType(type).build());}catch(Exception e){throw new StorageUnavailableException("Unable to store document",e);}}
 @Override public byte[] load(String key){try(var stream=client.getObject(GetObjectArgs.builder().bucket(bucket).object(key).build())){return stream.readAllBytes();}catch(Exception e){throw new StorageUnavailableException("Unable to load document",e);}}
 @Override public void archive(String key){/* Objects remain private and durable; metadata controls archival. */}
 @Override public void delete(String key){try{client.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(key).build());}catch(Exception e){throw new StorageUnavailableException("Unable to remove incomplete document",e);}}
 private void ensureBucket()throws Exception{if(!client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build()))client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());}
}

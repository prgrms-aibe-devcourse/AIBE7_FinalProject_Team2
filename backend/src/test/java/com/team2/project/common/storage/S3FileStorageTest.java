package com.team2.project.common.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Utilities;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/** S3 업로드 (COMMON-47, BE-91). S3Client는 가짜(Mockito)로 대체하고, URL 조립(S3Utilities)만 실제 구현을 쓴다 */
class S3FileStorageTest {

	private final S3Client s3Client = mock(S3Client.class);

	@BeforeEach
	void setUp() {
		// getUrl()은 네트워크를 타지 않고 문자열만 조립하므로 리전만 있으면 실제 구현을 그대로 쓸 수 있다
		when(s3Client.utilities()).thenReturn(S3Utilities.builder().region(Region.AP_NORTHEAST_2).build());
	}

	@Test
	@DisplayName("버킷에 올리고, 키와 공개 URL을 돌려준다")
	void upload_putsObjectAndReturnsUrl() {
		S3FileStorage storage = new S3FileStorage(s3Client, new S3Properties("my-bucket", ""));

		FileStorage.UploadedFile result = storage.upload(new byte[] {1, 2, 3}, "case-photos/case-1/a.jpg", "image/jpeg");

		ArgumentCaptor<PutObjectRequest> captor = ArgumentCaptor.forClass(PutObjectRequest.class);
		verify(s3Client).putObject(captor.capture(), any(RequestBody.class));
		assertThat(captor.getValue().bucket()).isEqualTo("my-bucket");
		assertThat(captor.getValue().key()).isEqualTo("case-photos/case-1/a.jpg");
		assertThat(captor.getValue().contentType()).isEqualTo("image/jpeg");
		assertThat(result.key()).isEqualTo("case-photos/case-1/a.jpg");
		assertThat(result.url()).contains("my-bucket").contains("case-photos/case-1/a.jpg");
	}

	@Test
	@DisplayName("dirPrefix가 있으면 실제 S3 키 앞에 붙이지만, 돌려주는 key는 호출한 쪽이 넘긴 값 그대로다")
	void upload_withDirPrefix_prependsOnlyForS3Key() {
		S3FileStorage storage = new S3FileStorage(s3Client, new S3Properties("my-bucket", "local"));

		FileStorage.UploadedFile result = storage.upload(new byte[] {1}, "case-photos/case-1/a.jpg", "image/jpeg");

		ArgumentCaptor<PutObjectRequest> captor = ArgumentCaptor.forClass(PutObjectRequest.class);
		verify(s3Client).putObject(captor.capture(), any(RequestBody.class));
		assertThat(captor.getValue().key()).isEqualTo("local/case-photos/case-1/a.jpg");
		assertThat(result.key()).isEqualTo("case-photos/case-1/a.jpg");
		assertThat(result.url()).contains("local/case-photos/case-1/a.jpg");
	}

	@Test
	@DisplayName("버킷 설정이 없으면 꺼져 있고, 업로드를 거절한다")
	void noBucket_isDisabled() {
		S3FileStorage storage = new S3FileStorage(s3Client, new S3Properties("", ""));

		assertThat(storage.enabled()).isFalse();
		assertThatThrownBy(() -> storage.upload(new byte[] {1}, "key", "image/jpeg"))
			.isInstanceOf(FileStorageException.class)
			.hasMessageContaining("꺼져");
	}

	@Test
	@DisplayName("키가 비어 있으면 거절한다")
	void blankKey_isRejected() {
		S3FileStorage storage = new S3FileStorage(s3Client, new S3Properties("my-bucket", ""));

		assertThatThrownBy(() -> storage.upload(new byte[] {1}, " ", "image/jpeg"))
			.isInstanceOf(FileStorageException.class)
			.hasMessageContaining("비어");
	}

	@Test
	@DisplayName("SDK 호출이 실패하면 FileStorageException으로 감싼다")
	void sdkException_isWrapped() {
		when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
			.thenThrow(SdkException.create("boom", null));
		S3FileStorage storage = new S3FileStorage(s3Client, new S3Properties("my-bucket", ""));

		assertThatThrownBy(() -> storage.upload(new byte[] {1}, "key", "image/jpeg"))
			.isInstanceOf(FileStorageException.class)
			.hasMessageContaining("업로드에 실패");
	}
}

import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { MediaService } from './media';

describe('MediaService', () => {
  let service: MediaService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(MediaService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('uploads images through the API Gateway', () => {
    const file = new File(['image'], 'photo.png', { type: 'image/png' });

    service.upload(file).subscribe();

    const request = http.expectOne('http://localhost:8080/media/images');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toBeInstanceOf(FormData);
    request.flush({
      id: 'media-1',
      url: '/api/media/images/media-1',
      originalFileName: 'photo.png',
      contentType: 'image/png',
      size: 5,
    });
  });

  it('builds a Gateway image URL', () => {
    expect(service.getImageUrl('media-1'))
      .toBe('http://localhost:8080/media/images/media-1');
  });
});

package mock

/**
 * Mock Docker class from docker-workflow plugin.
 */
class HttpResponse {

  public String content
  public int status_code

  HttpResponse(String content, int status_code) {
    this.content = content
    this.status_code = status_code
  }

  String getContent() {
    return this.content
  }

  int getStatus() {
    return this.status_code
  }
}

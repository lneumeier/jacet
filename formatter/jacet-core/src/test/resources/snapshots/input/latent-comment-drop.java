class LatentCommentDrop {
  // block-comment-drop-brace-comma
  void blockCommentBraceComma(Service service, Entity entity) {
    service.ifPresent(
      val -> {
        entity.setA(val);
      },
      () -> {
        entity.setB("");
      }
    );
  }
}

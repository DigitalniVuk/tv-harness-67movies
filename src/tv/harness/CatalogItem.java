package tv.harness;

final class CatalogItem {
    final String title;
    final String watchUrl;
    final String posterUrl;
    final String details;

    CatalogItem(String title, String watchUrl, String posterUrl, String details) {
        this.title = title;
        this.watchUrl = watchUrl;
        this.posterUrl = posterUrl;
        this.details = details;
    }
}

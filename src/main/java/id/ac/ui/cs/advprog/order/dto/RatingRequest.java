package id.ac.ui.cs.advprog.order.dto;

public class RatingRequest {
    private int productRating;
    private int jastiperRating;
    private String comment;

    public int getProductRating() { return productRating; }
    public void setProductRating(int productRating) { this.productRating = productRating; }
    public int getJastiperRating() { return jastiperRating; }
    public void setJastiperRating(int jastiperRating) { this.jastiperRating = jastiperRating; }
    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
}

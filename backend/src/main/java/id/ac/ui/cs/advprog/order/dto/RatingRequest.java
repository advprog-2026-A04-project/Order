package id.ac.ui.cs.advprog.order.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public class RatingRequest {
    @Min(value = 1, message = "productRating must be between 1 and 5")
    @Max(value = 5, message = "productRating must be between 1 and 5")
    private int productRating;
    @Min(value = 1, message = "jastiperRating must be between 1 and 5")
    @Max(value = 5, message = "jastiperRating must be between 1 and 5")
    private int jastiperRating;
    private String comment;

    public int getProductRating() { return productRating; }
    public void setProductRating(int productRating) { this.productRating = productRating; }
    public int getJastiperRating() { return jastiperRating; }
    public void setJastiperRating(int jastiperRating) { this.jastiperRating = jastiperRating; }
    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
}

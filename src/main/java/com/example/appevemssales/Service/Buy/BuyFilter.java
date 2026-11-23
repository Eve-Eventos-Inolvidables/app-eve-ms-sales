package com.example.appevemssales.Service.Buy;

import com.example.appevecommon.Service.Utilities.Responses.PagedFilter;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BuyFilter extends PagedFilter {
    private Long eventId;
    private Long userId;
}

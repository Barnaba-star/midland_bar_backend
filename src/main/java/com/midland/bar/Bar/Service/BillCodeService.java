package com.midland.bar.Bar.Service;

import com.midland.bar.Bar.Model.BillCode;
import com.midland.bar.Bar.Projection.BillCodeProjection;
import com.midland.bar.Bar.Repository.BillCodeRepository;
import com.midland.bar.Config.Security.LoggerUser;
import com.midland.bar.Utils.Responses.Response;
import com.midland.bar.Utils.Responses.ResponseList;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

/** The codes bills are opened under, as set up in POS Setting. */
@Service
@RequiredArgsConstructor
public class BillCodeService {

    private final BillCodeRepository billCodeRepository;

    public Response<BillCode> save(String rawCode) {
        String code = rawCode == null ? "" : rawCode.trim().toUpperCase();
        if (code.isEmpty())
            return new Response<>("Enter a code");
        if (code.length() > 30)
            return new Response<>("A code can be at most 30 characters");
        if (billCodeRepository.findByCode(LoggerUser.getBranchUID(), code).isPresent())
            return new Response<>("Code " + code + " already exists");
        BillCode billCode = new BillCode();
        billCode.setCode(code);
        return new Response<>(billCodeRepository.save(billCode));
    }

    public Response<BillCode> delete(String uid) {
        Optional<BillCode> found = billCodeRepository.findByUid(uid, LoggerUser.getBranchUID());
        if (found.isEmpty())
            return new Response<>("Code Not Found");
        BillCode billCode = found.get();
        if (!isAvailable(billCode.getCode()))
            return new Response<>("Code " + billCode.getCode() + " is on an unpaid bill - pay that bill first");
        billCodeRepository.delete(billCode);
        return new Response<>(billCode);
    }

    public ResponseList<BillCodeProjection> findAll() {
        return new ResponseList<>(billCodeRepository.findAllWithUse(LoggerUser.getBranchUID()));
    }

    public ResponseList<String> findAvailable() {
        return new ResponseList<>(billCodeRepository.findAvailableCodes(LoggerUser.getBranchUID()));
    }

    /** Set up in this branch and not held by an unpaid bill. */
    public boolean isAvailable(String code) {
        return code != null && billCodeRepository.findAvailableCodes(LoggerUser.getBranchUID()).contains(code);
    }
}

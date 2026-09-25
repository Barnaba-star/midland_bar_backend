package com.midland.bar.Setting.Service;

import com.midland.bar.Config.Security.LoggerUser;
import com.midland.bar.Setting.Model.Region;
import com.midland.bar.Setting.Repository.BranchRepository;
import com.midland.bar.Setting.Repository.RegionRepository;
import com.midland.bar.Utils.Responses.Response;
import com.midland.bar.Utils.Responses.ResponseList;
import lombok.RequiredArgsConstructor;
import lombok.extern.java.Log;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;

/**
 * The regions branches can be opened in.
 *
 * Adding one used to mean editing a map in BranchCodeHelper and deploying,
 * which is a poor reason to be unable to register a customer.
 */
@Service
@Log
@RequiredArgsConstructor
public class RegionService {

    private final RegionRepository regionRepository;
    private final BranchRepository branchRepository;

    public ResponseList<Region> findRegions() {
        return new ResponseList<>(regionRepository.findAllOrdered());
    }

    @Transactional
    public Response<Region> saveRegion(Region incoming) {
        if (incoming == null || incoming.getName() == null || incoming.getName().isBlank()) {
            return new Response<>("Provide the region name");
        }
        if (incoming.getCode() == null || incoming.getCode().isBlank()) {
            return new Response<>("Provide the region code");
        }

        String name = incoming.getName().trim().toUpperCase();
        String code = incoming.getCode().trim().toUpperCase();

        if (code.length() > 10) {
            return new Response<>("The code is too long");
        }

        Region region;
        if (incoming.getUid() != null && !incoming.getUid().isBlank()) {
            Optional<Region> existing = regionRepository.findById(incoming.getUid());
            if (existing.isEmpty()) {
                return new Response<>("Region Not Found");
            }
            region = existing.get();

            // Branch codes are built from this prefix and then stored on the
            // branch for good. Changing it once branches exist would leave the
            // same region holding DOD-001 and DDM-002, which nothing can
            // reconcile afterwards.
            boolean codeChanged = !code.equalsIgnoreCase(region.getCode());
            boolean nameChanged = !name.equalsIgnoreCase(region.getName());
            if (codeChanged || nameChanged) {
                long branches = branchRepository.countByRegion(region.getName());
                if (branches > 0) {
                    return new Response<>(branches + " branch(es) are registered in this region, so its name and code cannot change");
                }
            }
        } else {
            region = new Region();
        }

        // Two regions sharing a code would make branch codes ambiguous; two
        // sharing a name would make the lookup non-deterministic.
        Optional<Region> byName = regionRepository.findByName(name);
        if (byName.isPresent() && !byName.get().getUid().equals(region.getUid())) {
            return new Response<>("A region with that name already exists");
        }
        Optional<Region> byCode = regionRepository.findByCode(code);
        if (byCode.isPresent() && !byCode.get().getUid().equals(region.getUid())) {
            return new Response<>("A region with that code already exists");
        }

        region.setName(name);
        region.setCode(code);
        region.update();

        try {
            Region saved = regionRepository.save(region);
            log.info(LoggerUser.getEmail() + " saved region " + saved.getName());
            return new Response<>(saved);
        } catch (Exception e) {
            return new Response<>("Error in saving the region");
        }
    }

    @Transactional
    public Response<Region> deleteRegion(String uid) {
        Optional<Region> optional = regionRepository.findById(uid);
        if (optional.isEmpty()) {
            return new Response<>("Region Not Found");
        }
        Region region = optional.get();

        long branches = branchRepository.countByRegion(region.getName());
        if (branches > 0) {
            return new Response<>(branches + " branch(es) are registered in this region, so it cannot be removed");
        }

        region.delete();
        regionRepository.save(region);
        log.info(LoggerUser.getEmail() + " removed region " + region.getName());
        return new Response<>(region);
    }

    /** Copies the list that used to live in code, once, on first startup. */
    @Transactional
    public void seedIfMissing(Map<String, String> defaults) {
        if (!regionRepository.findAllOrdered().isEmpty()) {
            return;
        }
        defaults.forEach((name, code) -> {
            Region region = new Region();
            region.setName(name.toUpperCase());
            region.setCode(code.toUpperCase());
            regionRepository.save(region);
        });
        log.info("Seeded " + defaults.size() + " regions");
    }
}

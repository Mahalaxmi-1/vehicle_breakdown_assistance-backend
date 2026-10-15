package com.roadassist.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.roadassist.backend.dto.MechanicProfileUpdateRequest;
import com.roadassist.backend.model.User;
import com.roadassist.backend.repository.UserRepository;
import com.roadassist.backend.service.AddressGeocodingService;
import com.roadassist.backend.service.MechanicProfileService;
import com.roadassist.backend.service.geocoding.GeocodedLocation;

class MechanicProfileGeocodingTests {
    private final UserRepository users = mock(UserRepository.class);
    private final AddressGeocodingService geocoder = mock(AddressGeocodingService.class);
    private final MechanicProfileService service = new MechanicProfileService(users, geocoder);

    @Test
    void changedServiceAddressIsResolvedAndCoordinatesArePersisted() {
        User mechanic = mechanic("Old service address");
        when(users.findByEmailIgnoreCaseForUpdate(mechanic.getEmail())).thenReturn(Optional.of(mechanic));
        when(geocoder.resolve("New service address, Hyderabad"))
            .thenReturn(new GeocodedLocation(new BigDecimal("17.44"), new BigDecimal("78.39")));
        when(users.save(mechanic)).thenReturn(mechanic);

        var response = service.update(mechanic.getEmail(), request("New service address, Hyderabad"));

        assertEquals("New service address, Hyderabad", response.serviceLocation());
        assertEquals(new BigDecimal("17.44"), response.mechanicLatitude());
        assertEquals(new BigDecimal("78.39"), response.mechanicLongitude());
        verify(users).save(mechanic);
    }

    @Test
    void unchangedResolvedAddressDoesNotCallGeocoderAgain() {
        User mechanic = mechanic("Madhapur, Hyderabad");
        mechanic.setMechanicLatitude(new BigDecimal("17.44"));
        mechanic.setMechanicLongitude(new BigDecimal("78.39"));
        when(users.findByEmailIgnoreCaseForUpdate(mechanic.getEmail())).thenReturn(Optional.of(mechanic));
        when(users.save(mechanic)).thenReturn(mechanic);

        service.update(mechanic.getEmail(), request(" Madhapur,   Hyderabad "));

        verify(geocoder, never()).resolve(" Madhapur,   Hyderabad ");
        assertEquals(new BigDecimal("17.44"), mechanic.getMechanicLatitude());
    }

    private static User mechanic(String address) {
        User user = new User();
        user.setEmail("mechanic@example.test");
        user.setRole(User.Role.MECHANIC);
        user.setActive(true);
        user.setAvailability(User.Availability.OFFLINE);
        user.setServiceLocation(address);
        user.setSpecialization("General");
        return user;
    }

    private static MechanicProfileUpdateRequest request(String address) {
        return new MechanicProfileUpdateRequest("Mechanic", "9876543210", "General", address,
            4, null, null);
    }
}

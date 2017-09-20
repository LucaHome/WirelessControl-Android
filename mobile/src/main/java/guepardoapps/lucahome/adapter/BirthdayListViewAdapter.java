package guepardoapps.lucahome.adapter;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.Locale;

import com.rey.material.app.Dialog;
import com.rey.material.app.ThemeManager;
import com.rey.material.widget.FloatingActionButton;

import guepardoapps.lucahome.R;
import guepardoapps.lucahome.basic.classes.SerializableList;
import guepardoapps.lucahome.common.classes.LucaBirthday;
import guepardoapps.lucahome.common.dto.BirthdayDto;
import guepardoapps.lucahome.common.service.BirthdayService;
import guepardoapps.lucahome.service.NavigationService;
import guepardoapps.lucahome.views.BirthdayEditActivity;

public class BirthdayListViewAdapter extends BaseAdapter {
    private class Holder {
        private ImageView _birthdayImageView;
        private TextView _titleText;
        private TextView _dateText;
        private TextView _ageTextView;
        private FloatingActionButton _updateButton;
        private FloatingActionButton _deleteButton;

        private void displayDeleteDialog(@NonNull final LucaBirthday birthday) {
            final Dialog deleteDialog = new Dialog(_context);

            deleteDialog
                    .title(String.format(Locale.getDefault(), "Delete %s?", birthday.GetName()))
                    .positiveAction("Delete")
                    .negativeAction("Cancel")
                    .applyStyle(_isLightTheme ? R.style.SimpleDialogLight : R.style.SimpleDialog)
                    .setCancelable(true);

            deleteDialog.positiveActionClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    _birthdayService.DeleteBirthday(birthday);
                    deleteDialog.dismiss();
                }
            });

            deleteDialog.negativeActionClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    deleteDialog.dismiss();
                }
            });

            deleteDialog.show();
        }
    }

    private Context _context;
    private BirthdayService _birthdayService;
    private NavigationService _navigationService;

    private SerializableList<LucaBirthday> _listViewItems;

    private static LayoutInflater _inflater = null;
    private boolean _isLightTheme;

    public BirthdayListViewAdapter(@NonNull Context context, @NonNull SerializableList<LucaBirthday> listViewItems) {
        _context = context;
        _birthdayService = BirthdayService.getInstance();
        _navigationService = NavigationService.getInstance();

        _listViewItems = listViewItems;

        _inflater = (LayoutInflater) _context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        _isLightTheme = ThemeManager.getInstance().getCurrentTheme() == 0;
    }

    @Override
    public int getCount() {
        return _listViewItems.getSize();
    }

    @Override
    public Object getItem(int position) {
        return position;
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @SuppressLint({"InflateParams", "ViewHolder"})
    @Override
    public View getView(final int index, View convertView, ViewGroup parent) {
        final Holder holder = new Holder();

        View rowView = _inflater.inflate(R.layout.listview_card_birthday, null);

        holder._birthdayImageView = rowView.findViewById(R.id.birthday_card_image);
        holder._titleText = rowView.findViewById(R.id.birthday_card_title_text_view);
        holder._dateText = rowView.findViewById(R.id.birthday_date_text_view);
        holder._ageTextView = rowView.findViewById(R.id.birthday_age_text_view);
        holder._updateButton = rowView.findViewById(R.id.birthday_card_update_button);
        holder._deleteButton = rowView.findViewById(R.id.birthday_card_delete_button);

        final LucaBirthday birthday = _listViewItems.getValue(index);

        holder._birthdayImageView.setImageBitmap(birthday.GetPhoto());

        holder._titleText.setText(birthday.GetName());
        holder._dateText.setText(birthday.GetDate().DDMMYYYY());
        holder._ageTextView.setText(String.format(Locale.getDefault(), "%d years", birthday.GetAge()));

        if (birthday.HasBirthday()) {
            holder._titleText.setBackgroundColor(_context.getResources().getColor(R.color.LightRed));
        }

        holder._updateButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Bundle data = new Bundle();
                data.putSerializable(BirthdayService.BirthdayIntent, new BirthdayDto(birthday.GetId(), birthday.GetName(), birthday.GetDate(), BirthdayDto.Action.Update));
                _navigationService.NavigateToActivityWithData(_context, BirthdayEditActivity.class, data);
            }
        });

        holder._deleteButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                holder.displayDeleteDialog(birthday);
            }
        });

        return rowView;
    }
}
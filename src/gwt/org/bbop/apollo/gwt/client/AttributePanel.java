package org.bbop.apollo.gwt.client;

import com.google.gwt.cell.client.EditTextCell;
import com.google.gwt.cell.client.FieldUpdater;
import com.google.gwt.core.client.GWT;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.KeyUpEvent;
import com.google.gwt.http.client.Request;
import com.google.gwt.http.client.RequestCallback;
import com.google.gwt.http.client.Response;
import com.google.gwt.i18n.client.HasDirection;
import com.google.gwt.json.client.JSONParser;
import com.google.gwt.json.client.JSONValue;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.cellview.client.ColumnSortEvent;
import com.google.gwt.user.cellview.client.DataGrid;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.HasHorizontalAlignment;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwt.view.client.ListDataProvider;
import com.google.gwt.view.client.SelectionChangeEvent;
import com.google.gwt.view.client.SingleSelectionModel;
import org.bbop.apollo.gwt.client.dto.AnnotationInfo;
import org.bbop.apollo.gwt.client.dto.AttributeInfo;
import org.bbop.apollo.gwt.client.resources.TableResources;
import org.bbop.apollo.gwt.client.rest.AttributeRestService;
import org.bbop.apollo.gwt.client.rest.ProxyRestService;
import org.gwtbootstrap3.client.ui.Button;
import org.gwtbootstrap3.client.ui.ListBox;
import org.gwtbootstrap3.client.ui.TextBox;
import org.gwtbootstrap3.extras.bootbox.client.Bootbox;
import org.gwtbootstrap3.extras.bootbox.client.callback.ConfirmCallback;

import java.util.Comparator;
import java.util.List;

/**
 * Created by deepak.unni3 on 9/16/16.
 */
public class AttributePanel extends Composite {


    interface AttributePanelUiBinder extends UiBinder<Widget, AttributePanel> {
    }

    private static AttributePanelUiBinder ourUiBinder = GWT.create(AttributePanelUiBinder.class);

    DataGrid.Resources tablecss = GWT.create(TableResources.TableCss.class);
    @UiField(provided = true)
    DataGrid<AttributeInfo> dataGrid = new DataGrid<>(200, tablecss);
    @UiField
    TextBox tagInputBox;
    @UiField
    TextBox valueInputBox;
    @UiField
    Button addAttributeButton;
    @UiField
    Button deleteAttributeButton;
    @UiField
    ListBox cannedTagSelectorBox;
    @UiField
    ListBox cannedValueSelectorBox;

    private AnnotationInfo internalAnnotationInfo = null;
    private AttributeInfo internalAttributeInfo = null;
    private String oldTag, oldValue;
    private String tag, value;

    private static ListDataProvider<AttributeInfo> dataProvider = new ListDataProvider<>();
    private static List<AttributeInfo> attributeInfoList = dataProvider.getList();
    private SingleSelectionModel<AttributeInfo> selectionModel = new SingleSelectionModel<>();
    EditTextCell tagCell = new EditTextCell();
    EditTextCell valueCell = new EditTextCell();

    public AttributePanel() {

        initWidget(ourUiBinder.createAndBindUi(this));

        dataGrid.setWidth("100%");
        initializeTable();
        dataProvider.addDataDisplay(dataGrid);
        dataGrid.setSelectionModel(selectionModel);
        selectionModel.clear();
        deleteAttributeButton.setEnabled(false);
        selectionModel.addSelectionChangeHandler(new SelectionChangeEvent.Handler() {
            @Override
            public void onSelectionChange(SelectionChangeEvent selectionChangeEvent) {
                if (selectionModel.getSelectedSet().isEmpty()) {
                    deleteAttributeButton.setEnabled(false);
                } else {
                    selectAttributeData(selectionModel.getSelectedObject());
                    deleteAttributeButton.setEnabled(true);
                }
            }
        });

        resetCannedTagsAndValues();

    }

    private void resetCannedTagsAndValues() {
        cannedTagSelectorBox.clear();
        cannedTagSelectorBox.insertItem("Select canned tag", HasDirection.Direction.DEFAULT,null,0);
        cannedValueSelectorBox.clear();
        cannedValueSelectorBox.insertItem("Select canned value", HasDirection.Direction.DEFAULT,null,0);
    }

    public void initializeTable() {
        Column<AttributeInfo, String> tagColumn = new Column<AttributeInfo, String>(tagCell) {
            @Override
            public String getValue(AttributeInfo attributeInfo) {
                return attributeInfo.getTag();
            }
        };
        tagColumn.setFieldUpdater(new FieldUpdater<AttributeInfo, String>() {
            @Override
            public void update(int i, AttributeInfo object, String s) {
                if (s == null || s.trim().length() == 0) {
                    Bootbox.alert("Prefix can not be blank");
                    tagCell.clearViewData(object);
                    dataGrid.redrawRow(i);
                    redrawTable();
                } else if (!object.getTag().equals(s)) {
                    GWT.log("Tag Changed");
                    object.setTag(s);
                    selectAttributeData(object);
                    updateAttribute();
                }
            }
        });
        tagColumn.setSortable(true);
        tagColumn.setDefaultSortAscending(true);

        Column<AttributeInfo, String> valueColumn = new Column<AttributeInfo, String>(valueCell) {
            @Override
            public String getValue(AttributeInfo attributeInfo) {
                return attributeInfo.getValue();
            }
        };
        valueColumn.setFieldUpdater(new FieldUpdater<AttributeInfo, String>() {
            @Override
            public void update(int i, AttributeInfo object, String s) {
                if (s == null || s.trim().length() == 0) {
                    Bootbox.alert("Accession can not be blank");
                    valueCell.clearViewData(object);
                    dataGrid.redrawRow(i);
                    redrawTable();
                } else if (!object.getValue().equals(s)) {
                    GWT.log("Value Changed");
                    object.setValue(s);
                    selectAttributeData(object);
                    updateAttribute();
                }
            }
        });
        valueColumn.setHorizontalAlignment(HasHorizontalAlignment.ALIGN_LEFT);
        valueColumn.setSortable(true);

        dataGrid.addColumn(tagColumn, "Prefix");
        dataGrid.setColumnWidth(0, "100px");
        dataGrid.addColumn(valueColumn, "Accession");
        dataGrid.setColumnWidth(1, "100%");

        ColumnSortEvent.ListHandler<AttributeInfo> sortHandler = new ColumnSortEvent.ListHandler<AttributeInfo>(attributeInfoList);
        dataGrid.addColumnSortHandler(sortHandler);

        sortHandler.setComparator(tagColumn, new Comparator<AttributeInfo>() {
            @Override
            public int compare(AttributeInfo o1, AttributeInfo o2) {
                return o1.getTag().compareTo(o2.getTag());
            }
        });

        sortHandler.setComparator(valueColumn, new Comparator<AttributeInfo>() {
            @Override
            public int compare(AttributeInfo o1, AttributeInfo o2) {
                return o1.getValue().compareTo(o2.getValue());
            }
        });
        // default is ascending
        dataGrid.getColumnSortList().push(tagColumn);
        ColumnSortEvent.fire(dataGrid, dataGrid.getColumnSortList());
    }

    public void updateData(AnnotationInfo annotationInfo) {
        GWT.log("updating annotation info: " + annotationInfo);
        if (annotationInfo == null) {
            return;
        }
        this.internalAnnotationInfo = annotationInfo;
        attributeInfoList.clear();
        attributeInfoList.addAll(annotationInfo.getAttributeList());
        ColumnSortEvent.fire(dataGrid, dataGrid.getColumnSortList());
        GWT.log("List size: " + attributeInfoList.size());
        redrawTable();
        setVisible(true);
    }

    public void updateData() {
        updateData(null);
    }

    public void selectAttributeData(AttributeInfo v) {
        this.internalAttributeInfo = v;
        // tag
        this.oldTag = this.tag;
        this.tag = this.internalAttributeInfo.getTag();

        // value
        this.oldValue = this.value;
        this.value = this.internalAttributeInfo.getValue();

        redrawTable();
        setVisible(true);
    }

    public void updateAttribute() {
        if (validateTags()) {
            final AttributeInfo newAttributeInfo = new AttributeInfo(this.tag, this.value);
            RequestCallback requestCallBack = new RequestCallback() {
                @Override
                public void onResponseReceived(Request request, Response response) {
                    JSONValue returnValue = JSONParser.parseStrict(response.getText());
                    redrawTable();
                }

                @Override
                public void onError(Request request, Throwable exception) {
                    tagCell.clearViewData(newAttributeInfo);
                    dataGrid.redraw();
                    Bootbox.alert("Error updating variant info property: " + exception);
                    resetTags();
                    // TODO: reset data
                    redrawTable();
                }
            };
            AttributeRestService.updateAttribute(requestCallBack, this.internalAnnotationInfo, new AttributeInfo(this.oldTag, this.oldValue), newAttributeInfo);
            ;
        } else {
            resetTags();
        }
    }

    private void resetTags() {
        GWT.log("reseting tag");
        this.tag = this.oldTag;
        this.value = this.oldValue;
        updateData(this.internalAnnotationInfo);
        redrawTable();
    }

    public void redrawTable() {
        this.dataGrid.redraw();
    }

    @UiHandler("tagInputBox")
    public void tagInputBoxType(KeyUpEvent event) {
        addAttributeButton.setEnabled(validateTags());
    }


    @UiHandler("valueInputBox")
    public void valueInputBoxType(KeyUpEvent event) {
        addAttributeButton.setEnabled(validateTags());
    }

    private boolean validateTags() {
        collectTags();
        return this.tag != null && !this.tag.isEmpty() && this.value != null && !this.value.isEmpty();
    }

    private void collectTags() {
        this.tag = tagInputBox.getText();
        this.value = valueInputBox.getText();
    }



    @UiHandler("addAttributeButton")
    public void addAttributeButton(ClickEvent ce) {
        final AnnotationInfo internalAnnotationInfo = this.internalAnnotationInfo;
        if (validateTags()) {
            final AttributeInfo newAttributeInfo = new AttributeInfo(this.tag, this.value);
            this.tagInputBox.clear();
            this.valueInputBox.clear();

            RequestCallback requestCallBack = new RequestCallback() {
                @Override
                public void onResponseReceived(Request request, Response response) {
                    JSONValue returnValue = JSONParser.parseStrict(response.getText());
                    attributeInfoList.add(newAttributeInfo);
                    internalAnnotationInfo.setAttributeList(attributeInfoList);
                }

                @Override
                public void onError(Request request, Throwable exception) {
                    Bootbox.alert("Error updating variant info property: " + exception);
                    resetTags();
                    // TODO: reset data
                    redrawTable();
                }
            };
            AttributeRestService.addAttribute(requestCallBack, this.internalAnnotationInfo, newAttributeInfo);
        }
    }

    @UiHandler("deleteAttributeButton")
    public void deleteAttribute(ClickEvent ce) {

        if (this.internalAttributeInfo != null) {
            RequestCallback requestCallBack = new RequestCallback() {
                @Override
                public void onResponseReceived(Request request, Response response) {
                    JSONValue returnValue = JSONParser.parseStrict(response.getText());
                    deleteAttributeButton.setEnabled(false);
                    redrawTable();
                }

                @Override
                public void onError(Request request, Throwable exception) {
                    Bootbox.alert("Error deleting variant info property: " + exception);
                    redrawTable();
                }
            };
            AttributeRestService.deleteAttribute(requestCallBack, this.internalAnnotationInfo, this.internalAttributeInfo);
            ;
        }
    }
}